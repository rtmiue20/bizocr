package com.company.bizocr.company;

import com.company.bizocr.model.entity.Company;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;

/**
 * Tập trung logic "tìm công ty theo tên chuẩn hóa".
 *
 * <p><b>Vấn đề gốc:</b> {@code CompanyRepository.findByNormalizedName()} trả {@code Mono},
 * nhưng DB có thể có nhiều dòng cùng {@code normalized_name} (nợ kỹ thuật chưa có UNIQUE constraint).
 * Gọi {@code Mono} khi DB trả &gt;1 dòng sẽ ném {@code IncorrectResultSizeDataAccessException}.
 *
 * <p><b>Giải pháp:</b> dùng {@code findAllByNormalizedName()} (Flux) rồi chọn dòng ưu tiên:
 * <ol>
 *   <li>VERIFIED &gt; UNVERIFIED</li>
 *   <li>Có {@code tax_code} &gt; không có</li>
 *   <li>{@code id} nhỏ nhất (dòng cũ nhất = gốc)</li>
 * </ol>
 *
 * <p>Nếu {@code normalizedName} rỗng hoặc null → trả {@code Mono.empty()} ngay,
 * tránh khớp bừa với mọi thẻ không có tên công ty.
 *
 * <p><b>Race condition guard:</b> Sau khi có UNIQUE index, nếu 2 luồng đồng thời tạo cùng
 * 1 công ty thì luồng thua sẽ bắt {@code DataIntegrityViolationException} và đọc lại
 * dòng đã được luồng thắng tạo — không để nổ 500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyFinderService {

    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;

    /**
     * Tìm dòng company ưu tiên nhất theo {@code normalizedName}.
     * Chịu được DB có nhiều dòng cùng tên (không ném exception).
     *
     * @param normalizedName tên đã chuẩn hóa (kết quả của {@link CompanyNameNormalizer#normalize})
     * @return Mono của Company tốt nhất, hoặc {@code Mono.empty()} nếu không tìm thấy / tên rỗng
     */
    public Mono<Company> findBestByName(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            log.debug("[CompanyFinder] normalizedName rỗng — bỏ qua tra theo tên");
            return Mono.empty();
        }

        return companyRepository
                .findAllByNormalizedName(normalizedName)
                .filter(c -> !Boolean.FALSE.equals(c.getActive()))
                .collectList()
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        return Mono.empty();
                    }
                    if (list.size() > 1) {
                        log.warn("[CompanyFinder] DB có {} dòng trùng normalized_name='{}'. " +
                                "Chọn dòng ưu tiên (VERIFIED > có tax_code > id nhỏ nhất). " +
                                "Cần dọn dữ liệu trùng bằng dedup_companies.sql.",
                                list.size(), normalizedName);
                    }
                    Company best = list.stream()
                            .min(Comparator
                                    .comparingInt(CompanyFinderService::verificationRank)
                                    .thenComparingInt(c -> c.getTaxCode() != null && !c.getTaxCode().isBlank() ? 0 : 1)
                                    .thenComparingLong(c -> c.getId() != null ? c.getId() : Long.MAX_VALUE))
                            .orElseThrow(); // list không rỗng, không throw
                    return Mono.just(best);
                });
    }

    /**
     * Tìm theo tên rồi fallback sang alias.
     * Dùng trong các luồng cần alias lookup sau bước tìm theo normalized_name.
     */
    public Mono<Company> findBestByNameOrAlias(String normalizedName) {
        return findBestByName(normalizedName)
                .switchIfEmpty(Mono.defer(() ->
                        companyAliasRepository
                                .findByNormalizedAlias(normalizedName)
                                .flatMap(alias -> companyRepository.findById(alias.getCompanyId()))
                                .filter(c -> !Boolean.FALSE.equals(c.getActive()))
                ));
    }

    /**
     * Lưu company mới, chịu được race condition:
     * nếu luồng song song đã INSERT cùng normalized_name (DuplicateKeyException),
     * đọc lại dòng đó thay vì nổ 500.
     *
     * <p>Dùng ở mọi chỗ tạo Company mới thay thế cho {@code companyRepository.save()} trực tiếp.
     *
     * @param company entity chưa có id
     * @return Mono của Company đã được lưu (hoặc đọc lại từ DB nếu thua race)
     */
    public Mono<Company> saveOrFetch(Company company) {
        return companyRepository.save(company)
                .onErrorResume(DataIntegrityViolationException.class, ex -> {
                    // Thua race condition: luồng khác đã INSERT cùng normalized_name
                    // → đọc lại dòng đó thay vì nổ exception
                    String normalizedName = company.getNormalizedName();
                    log.warn("[CompanyFinder] DuplicateKey khi tạo company normalized_name='{}'. " +
                            "Đọc lại dòng đã tồn tại. ({})", normalizedName, ex.getMessage());
                    return findBestByName(normalizedName)
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "[CompanyFinder] DuplicateKey nhưng không tìm lại được dòng " +
                                    "normalized_name='" + normalizedName + "' — dữ liệu không nhất quán")));
                });
    }

    /** 0 = tốt nhất (VERIFIED), 2 = UNVERIFIED/null */
    private static int verificationRank(Company c) {
        if (c.getVerificationStatus() == null) return 2;
        return switch (c.getVerificationStatus()) {
            case VERIFIED   -> 0;
            case UNVERIFIED -> 2;
        };
    }
}
