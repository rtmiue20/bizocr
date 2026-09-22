package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.client.TaxApiClient;
import com.company.bizocr.company.CompanyNameNormalizer;
import com.company.bizocr.model.dto.request.DocumentApproveRequest;
import com.company.bizocr.model.dto.response.DocumentApproveResponse;
import com.company.bizocr.model.dto.response.TaxLookupResponse;
import com.company.bizocr.model.entity.Company;
import com.company.bizocr.model.entity.CompanyVerificationStatus;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.DocumentStatus;
import com.company.bizocr.repository.CompanyAliasRepository;
import com.company.bizocr.repository.CompanyRepository;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.repository.IndustryRepository;
import com.company.bizocr.security.CurrentUser;
import com.company.bizocr.statemachine.DocumentStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bước 0 — characterization tests cho DocumentApprovalService.approveDocument(),
 * tập trung vào nhánh upsertCompany() (private, chỉ test được qua entry point công khai).
 *
 * Cần trong pom/gradle (test scope): mockito-core, reactor-test, assertj-core, jackson-databind
 * (đều nằm trong spring-boot-starter-test + spring-boot-starter-webflux nếu project đã dùng).
 *
 * LƯU Ý: DocumentApprovalService dùng @Transactional — trong unit test thuần (không có
 * Spring context) annotation này không có tác dụng, nên transaction KHÔNG được test ở đây.
 * Nếu muốn test rollback thật, cần @DataR2dbcTest hoặc @SpringBootTest riêng, ngoài phạm vi Bước 0.
 */
class DocumentApprovalServiceTest {

    private DocumentRepository documentRepository;
    private CompanyRepository companyRepository;
    private CompanyAliasRepository aliasRepository;
    private TaxApiClient taxApiClient;
    private PythonMlClient pythonMlClient;
    private CompanyNameNormalizer nameNormalizer;
    private ExternalTaxSearchService externalTaxSearchService;
    private IndustryRepository industryRepository;
    private FieldAuditService fieldAuditService;
    private DocumentStateMachine stateMachine;
    private DocumentApprovalService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        companyRepository = mock(CompanyRepository.class);
        aliasRepository = mock(CompanyAliasRepository.class);
        taxApiClient = mock(TaxApiClient.class);
        pythonMlClient = mock(PythonMlClient.class);
        nameNormalizer = new CompanyNameNormalizer(); // implementation thật
        externalTaxSearchService = mock(ExternalTaxSearchService.class);
        industryRepository = mock(IndustryRepository.class);
        fieldAuditService = mock(FieldAuditService.class);
        stateMachine = mock(DocumentStateMachine.class);

        service = new DocumentApprovalService(
                documentRepository, companyRepository, aliasRepository, taxApiClient,
                new ObjectMapper(), pythonMlClient, nameNormalizer, externalTaxSearchService,
                industryRepository, fieldAuditService, stateMachine);

        // Stub các nhánh phụ không liên quan tới bất biến đang test, để test tập trung vào upsertCompany
        when(fieldAuditService.logFieldCorrections(any(Document.class), anyString())).thenReturn(Mono.empty());
        when(pythonMlClient.collectApprovedData(anyString(), anyString())).thenReturn(Mono.empty());
        when(externalTaxSearchService.searchCompanyInfoOnline(anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("bỏ qua trong test này")));
        when(industryRepository.findByCodeInAndIsActiveTrue(any())).thenReturn(Flux.empty());
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(aliasRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // FSM stub: mặc định trả SUCCESS để các test upsertCompany không bị chặn ở FSM
        when(stateMachine.transition(anyLong(), any(), any()))
                .thenReturn(Mono.just(DocumentStateMachine.TransitionResult.SUCCESS));
    }


    private Document existingDoc() {
        Document doc = new Document();
        doc.setId(42L);
        doc.setStatus(DocumentStatus.NEEDS_REVIEW.name());
        doc.setRawOcrText("CONG TY ABC");
        return doc;
    }

    // ------------------------------------------------------------------
    // Baseline: chưa có company nào theo taxCode -> tạo mới, VERIFIED-ready.
    // Test này XANH trên code hiện tại.
    // ------------------------------------------------------------------
    @Test
    void approveDocument_createsNewCompany_whenTaxCodeNotFoundYet() {
        when(documentRepository.findById(42L)).thenReturn(Mono.just(existingDoc()));
        when(companyRepository.findByTaxCode("0101234567")).thenReturn(Mono.empty());
        when(taxApiClient.lookup(eq("0101234567"), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder()
                        .taxCode("0101234567").legalName("Cong Ty ABC").address("123 Nguyen Hue").build()));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        DocumentApproveRequest req = DocumentApproveRequest.builder()
                .taxCode("0101234567").companyName("Cong Ty ABC").approvedType("INVOICE")
                .updatedFields(Map.of("companyName", "Cong Ty ABC"))
                .build();

        CurrentUser reviewer = new CurrentUser("nguyen.van.a", List.of("REVIEWER"), 1L);

        StepVerifier.create(service.approveDocument(42L, req, reviewer))
                .assertNext(resp -> assertThat(resp.getStatus()).isEqualTo("APPROVED"))
                .verifyComplete();

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(saved.capture());
        assertThat(saved.getValue().getAddress()).isEqualTo("123 Nguyen Hue");

        ArgumentCaptor<Document> savedDoc = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getReviewedBy())
                .as("reviewedBy phải lấy từ JWT (CurrentUser), không phải từ request body")
                .isEqualTo("nguyen.van.a");
    }

    // ------------------------------------------------------------------
    // Bất biến #1 (chiều "phải nâng cấp khi có xác nhận thật"): khi duyệt một tài liệu
    // với taxCode TRÙNG một company đã tồn tại (tạo trước đó ở trạng thái UNVERIFIED bởi
    // CompanyResolverService khi chỉ có OCR, không tra được Tax API), và lần duyệt NÀY
    // Tax API tra thành công (tax.getTaxCode() != null) -> company phải được cập nhật
    // verificationStatus/legalName/address theo dữ liệu Tax API mới.
    //
    // ĐANG ĐỎ trên code hiện tại. upsertByTaxCode() chỉ chạy switchIfEmpty(...) khi
    // findByTaxCode() KHÔNG thấy gì. Nếu company đã tồn tại (dù đang UNVERIFIED), nhánh
    // switchIfEmpty bị bỏ qua hoàn toàn -> company y nguyên, không có field nào được set,
    // verificationStatus vẫn UNVERIFIED dù người duyệt vừa xác nhận và Tax API vừa xác thực.
    //
    // Cách sửa: trong upsertByTaxCode(), nhánh company ĐÃ tồn tại cũng phải cập nhật
    // legalName/address/verificationStatus khi tax.getTaxCode() != null — giống hệt logic
    // createOrUpgradeVerified() bên CompanyResolverService (đây cũng là lý do nên hợp nhất
    // 2 nơi này thành 1 hàm dùng chung, đúng như mục 3 của lộ trình 5 bước).
    // ------------------------------------------------------------------
    @Test
    void approveDocument_upgradesExistingUnverifiedCompany_whenTaxApiConfirmsSameTaxCode() {
        Company existingUnverified = Company.builder()
                .id(7L).taxCode("0101234567").legalName("Cong Ty ABC (chua xac thuc)")
                .normalizedName("CONG TY ABC").active(true)
                .verificationStatus(CompanyVerificationStatus.UNVERIFIED)
                .build();

        when(documentRepository.findById(42L)).thenReturn(Mono.just(existingDoc()));
        when(companyRepository.findByTaxCode("0101234567")).thenReturn(Mono.just(existingUnverified));
        when(taxApiClient.lookup(eq("0101234567"), anyString())).thenReturn(Mono.just(
                TaxLookupResponse.builder()
                        .taxCode("0101234567").legalName("Cong Ty ABC").address("123 Nguyen Hue").build()));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        DocumentApproveRequest req = DocumentApproveRequest.builder()
                .taxCode("0101234567").companyName("Cong Ty ABC").approvedType("INVOICE")
                .updatedFields(Map.of("companyName", "Cong Ty ABC"))
                .build();

        CurrentUser reviewer = new CurrentUser("nguyen.van.a", List.of("REVIEWER"), 1L);

        StepVerifier.create(service.approveDocument(42L, req, reviewer))
                .assertNext(resp -> {})
                .verifyComplete();

        // Mong muốn: company được lưu lại với verificationStatus = VERIFIED.
        // Hiện tại: company hoặc không được save() lần nào, hoặc được save() nhưng
        // verificationStatus vẫn UNVERIFIED — cả hai đều là bug, assert dưới đây sẽ đỏ.
        verify(companyRepository, atLeastOnce()).save(argThat(c ->
                c.getVerificationStatus() == CompanyVerificationStatus.VERIFIED));
    }
}