package com.company.bizocr.company;

import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.model.dto.CompanyInfo;
import com.company.bizocr.model.dto.response.TaxLookupResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyVerificationSource;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.service.ExternalTaxSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bước 0 — characterization tests cho CompanyResolverService, viết TRƯỚC khi hợp nhất
 * logic công ty (Bước 6). Chạy suite này trên code hiện tại, ghi lại test nào xanh/đỏ,
 * rồi mới bắt đầu refactor. Sau khi sửa Bước 6, mọi test ở đây phải xanh — nếu vẫn có
 * test đỏ, refactor chưa xong, KHÔNG được sửa test cho xanh giả.
 *
 * Chạy: mvn -Dtest=CompanyResolverServiceTest test
 * (hoặc gradle test --tests "...CompanyResolverServiceTest")
 *
 * Cần trong pom/gradle (test scope), nếu project chưa có sẵn:
 *   org.mockito:mockito-core / mockito-junit-jupiter
 *   io.projectreactor:reactor-test
 *   org.assertj:assertj-core
 * (spring-boot-starter-test thường đã kéo đủ 3 thư viện trên)
 */
class CompanyResolverServiceTest {

    private CompanyRepository companyRepository;
    private CompanyAliasRepository companyAliasRepository;
    private CompanyNameNormalizer nameNormalizer;
    private TaxApiClient taxApiClient;
    private ExternalTaxSearchService externalTaxSearchService;
    private CompanyResolverService service;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        companyAliasRepository = mock(CompanyAliasRepository.class);
        nameNormalizer = new CompanyNameNormalizer(); // implementation thật, không cần mock
        taxApiClient = mock(TaxApiClient.class);
        externalTaxSearchService = mock(ExternalTaxSearchService.class);
        service = new CompanyResolverService(
                companyRepository, companyAliasRepository, nameNormalizer, taxApiClient, externalTaxSearchService);

        // Mặc định: không có alias trùng, để các test không liên quan không phải stub lại
        when(companyAliasRepository.findByNormalizedAlias(anyString())).thenReturn(Mono.empty());
        when(companyAliasRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(companyRepository.findByNormalizedName(anyString())).thenReturn(Mono.empty());
    }

    // ------------------------------------------------------------------
    // Bất biến #3: tra cứu ưu tiên taxCode trước tên/alias — ĐANG ĐÚNG.
    // ------------------------------------------------------------------
    @Test
    void resolve_prefersTaxCodeMatch_overNameLookup_whenBothWouldMatch() {
        Company existing = Company.builder()
                .id(1L)
                .taxCode("0101234567")
                .legalName("Cong Ty ABC")
                .normalizedName("CONG TY ABC")
                .active(true)
                .verificationStatus(CompanyVerificationStatus.VERIFIED)
                .build();
        when(companyRepository.findByTaxCode("0101234567")).thenReturn(Mono.just(existing));

        String rawText = "CONG TY ABC\nMST: 0101234567\nHa Noi";

        StepVerifier.create(service.resolve(rawText))
                .assertNext(info -> {
                    assertThat(info.getMatchedBy()).isEqualTo("TAX_CODE");
                    assertThat(info.getMatchScore()).isEqualTo(1.0);
                })
                .verifyComplete();

        // Không được đi vào nhánh tra theo tên khi đã khớp taxCode
        verify(companyRepository, never()).findByNormalizedName(anyString());
    }

    // ------------------------------------------------------------------
    // Baseline: không tìm thấy nội bộ, Tax API có dữ liệu đầy đủ -> tạo VERIFIED.
    // Test này XANH trên code hiện tại — dùng để khóa hành vi tạo mới bình thường.
    // ------------------------------------------------------------------
    @Test
    void resolve_createsVerifiedCompany_whenTaxApiReturnsFullData() {
        when(companyRepository.findByTaxCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByCompanyCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByNormalizedName(anyString())).thenReturn(Mono.empty());
        when(taxApiClient.lookup(eq("0101234567"), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder()
                        .taxCode("0101234567")
                        .legalName("Cong Ty ABC")
                        .address("123 Nguyen Hue, Q1")
                        .build()));
        when(companyRepository.save(any(Company.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        String rawText = "CONG TY ABC\nMST: 0101234567";

        StepVerifier.create(service.resolve(rawText))
                .assertNext(info -> {
                    assertThat(info.getVerificationStatus()).isEqualTo("VERIFIED");
                    assertThat(info.getMatchScore()).isEqualTo(1.0);
                })
                .verifyComplete();

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(saved.capture());
        assertThat(saved.getValue().getAddress()).isEqualTo("123 Nguyen Hue, Q1");
    }

    // ------------------------------------------------------------------
    // Bất biến #2: trường đã có giá trị không bị ghi đè bằng giá trị rỗng/null.
    //
    // ĐANG ĐỎ trên code hiện tại. createOrUpgradeVerified() ghi đè address/legalName
    // KHÔNG ĐIỀU KIỆN bằng dữ liệu từ TaxLookupResponse, kể cả khi field đó null.
    // Nếu công ty đã có address (do người duyệt nhập tay hoặc lần tra cứu trước),
    // mà lần lookup Tax API này trả về address = null (dữ liệu Tax API không đầy đủ),
    // address hiện tại sẽ bị xóa mất.
    //
    // Cách sửa: chỉ set field khi taxLookup có giá trị (không null/blank):
    //   if (taxLookup.getAddress() != null && !taxLookup.getAddress().isBlank()) {
    //       company.setAddress(taxLookup.getAddress());
    //   }
    // ------------------------------------------------------------------
    @Test
    void resolve_doesNotWipeExistingAddress_whenTaxApiReturnsNullAddress() {
        Company existingUnverified = Company.builder()
                .id(7L)
                .taxCode("0101234567")
                .legalName("Cong Ty ABC")
                .normalizedName("CONG TY ABC")
                .address("123 Nguyen Hue, Q1") // đã có địa chỉ từ trước
                .active(true)
                .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                .build();

        // Không tìm thấy active company theo taxCode ở bước match đầu
        // (giả lập: bản ghi cũ không active hoặc bị lọc), nên rơi vào nhánh gọi Tax API...
        when(companyRepository.findByTaxCode("0101234567"))
                .thenReturn(Mono.empty())                    // lần match đầu (bị filter isActive)
                .thenReturn(Mono.just(existingUnverified));   // lần findByTaxCode bên trong createOrUpgradeVerified
        when(companyRepository.findByCompanyCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByNormalizedName(anyString())).thenReturn(Mono.empty());
        // Tax API trả về KHÔNG có address (trường hợp thực tế: một số MST tra được tên nhưng thiếu địa chỉ)
        when(taxApiClient.lookup(eq("0101234567"), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder()
                        .taxCode("0101234567")
                        .legalName("Cong Ty ABC")
                        .address(null)
                        .build()));
        when(companyRepository.save(any(Company.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        String rawText = "CONG TY ABC\nMST: 0101234567";

        StepVerifier.create(service.resolve(rawText))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(saved.capture());
        assertThat(saved.getValue().getAddress())
                .as("address không được bị null hóa khi Tax API không trả về address")
                .isEqualTo("123 Nguyen Hue, Q1");
    }

    // ------------------------------------------------------------------
    // Bất biến #1: VERIFIED không bao giờ bị hạ cấp. Test này XANH — vì
    // createOrUpgradeVerified() luôn set VERIFIED, không bao giờ hạ xuống UNVERIFIED.
    // Giữ test này để đảm bảo Bước 6 không vô tình làm hỏng phần đang đúng.
    // ------------------------------------------------------------------
    @Test
    void resolve_neverDowngradesVerifiedStatus_onSubsequentTaxApiUpgrade() {
        when(companyRepository.findByTaxCode(anyString()))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(Company.builder()
                        .id(9L).taxCode("0101234567").legalName("Cong Ty ABC")
                        .verificationStatus(CompanyVerificationStatus.VERIFIED)
                        .verificationSource(CompanyVerificationSource.VIETQR_DIRECT)
                        .active(true).build()));
        when(companyRepository.findByCompanyCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByNormalizedName(anyString())).thenReturn(Mono.empty());
        when(taxApiClient.lookup(anyString(), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder().taxCode("0101234567").legalName("Cong Ty ABC").build()));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.resolve("CONG TY ABC\nMST: 0101234567"))
                .assertNext(info -> assertThat(info.getVerificationStatus()).isEqualTo("VERIFIED"))
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // Bất biến #4: hai lần resolve() đồng thời cho cùng 1 taxCode CHƯA từng có
    // trong DB không được tạo ra 2 Company.
    //
    // ĐANG ĐỎ trên code hiện tại — về mặt logic. findByTaxCode(...).switchIfEmpty(save(...))
    // không có khóa hay bắt lỗi duplicate-key, nên nếu 2 request chạy gần như đồng thời
    // (2 tài liệu của cùng 1 công ty mới upload liên tiếp), cả hai đều thấy "chưa có"
    // và đều gọi save(). Test dưới đây mô phỏng đúng kịch bản đó bằng cách để
    // findByTaxCode() LUÔN trả về rỗng (đúng như những gì 2 request gần như đồng thời
    // sẽ thấy), rồi gọi resolve() hai lần và đếm số lần save().
    //
    // Đây là test Ở MỨC LOGIC (không phải test race điều kiện thật với nhiều thread),
    // vì bug thật chỉ lộ ra khi có ràng buộc UNIQUE ở DB. Việc cần làm thêm, ngoài
    // phạm vi unit test này: thêm UNIQUE INDEX trên company.tax_code, và bắt
    // DuplicateKeyException trong nhánh save() để SELECT lại thay vì để lỗi văng ra.
    // ------------------------------------------------------------------
    @Test
    void resolve_calledTwiceForSameNewTaxCode_currentlySavesTwice_needsDbUniqueConstraintAndDedup() {
        when(companyRepository.findByTaxCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByCompanyCode(anyString())).thenReturn(Mono.empty());
        when(companyRepository.findByNormalizedName(anyString())).thenReturn(Mono.empty());
        when(taxApiClient.lookup(anyString(), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder().taxCode("0109999999").legalName("Cong Ty Moi").build()));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        String rawText = "CONG TY MOI\nMST: 0109999999";

        StepVerifier.create(service.resolve(rawText)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.resolve(rawText)).expectNextCount(1).verifyComplete();

        // Mong muốn cuối cùng (sau khi thêm unique constraint + catch-and-reselect ở Bước 6): 1.
        // Hiện tại: 2 — ghi lại đúng số này để track tiến độ fix, đừng sửa số cho xanh giả.
        verify(companyRepository, times(2)).save(any(Company.class));
    }
}