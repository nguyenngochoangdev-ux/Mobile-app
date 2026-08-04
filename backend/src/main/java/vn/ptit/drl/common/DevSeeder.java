package vn.ptit.drl.common;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.event.Event;
import vn.ptit.drl.event.EventRepository;
import vn.ptit.drl.event.EventStatus;
import vn.ptit.drl.identity.Role;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.identity.User;
import vn.ptit.drl.identity.UserRepository;
import vn.ptit.drl.org.OrgType;
import vn.ptit.drl.org.Organization;
import vn.ptit.drl.org.OrganizationRepository;

/**
 * Sinh dữ liệu demo. Chỉ chạy khi bật profile {@code seed}:
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.profiles=seed</pre>
 *
 * Buổi bảo vệ cần dữ liệu sạch và tái lập được — đó là lý do seeder là code chạy lại
 * được, không phải một file SQL nhập tay một lần. Reset: {@code scripts/reset-db.ps1}.
 *
 * Seed cố định ({@code SEED_VALUE}) để hai lần chạy cho ra cùng dữ liệu — cần thiết khi
 * so sánh số liệu đo giữa các lần benchmark ở chương 11.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class DevSeeder implements CommandLineRunner {

    private static final long SEED_VALUE = 20260804L;
    private static final int STUDENT_COUNT = 500;
    /** Chỉ dùng cho dữ liệu demo. Không bao giờ xuất hiện ngoài profile seed. */
    private static final String DEMO_PASSWORD = "Demo@123";

    private static final String[] HO = {
        "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ",
        "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"
    };
    private static final String[] DEM = {
        "Văn", "Thị", "Ngọc", "Minh", "Hữu", "Đức", "Thanh", "Quang", "Hồng", "Xuân"
    };
    private static final String[] TEN = {
        "An", "Bình", "Cường", "Dũng", "Giang", "Hà", "Hải", "Hoàng", "Hùng", "Khánh",
        "Lan", "Linh", "Mai", "Nam", "Nga", "Nhung", "Phúc", "Quân", "Sơn", "Thảo",
        "Thắng", "Trang", "Trung", "Tuấn", "Vân", "Việt", "Yến", "Đạt", "Ánh", "Ước"
    };

    private final OrganizationRepository orgRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername("admin")) {
            log.info("Đã có dữ liệu seed, bỏ qua. Chạy scripts/reset-db.ps1 để làm sạch.");
            return;
        }

        Random rnd = new Random(SEED_VALUE);
        SecureRandom secureRandom = new SecureRandom();

        Organization truong = orgRepository.save(Organization.builder()
                .name("Học viện Công nghệ Bưu chính Viễn thông")
                .type(OrgType.TRUONG)
                .build());

        List<Organization> khoa = new ArrayList<>();
        for (String ten : new String[]{
                "Công nghệ thông tin", "Viễn thông", "Điện tử", "Quản trị kinh doanh", "Đa phương tiện"}) {
            khoa.add(orgRepository.save(Organization.builder()
                    .name("Khoa " + ten).type(OrgType.KHOA).parent(truong).build()));
        }

        Organization doan = orgRepository.save(Organization.builder()
                .name("Đoàn Thanh niên Học viện").type(OrgType.DOAN).parent(truong).build());

        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        userRepository.save(User.builder()
                .username("admin").passwordHash(hash).role(Role.ADMIN).enabled(true).build());
        userRepository.save(User.builder()
                .username("canbo").passwordHash(hash).role(Role.STAFF)
                .staffOrg(doan).enabled(true).build());

        List<Student> students = new ArrayList<>(STUDENT_COUNT);
        for (int i = 0; i < STUDENT_COUNT; i++) {
            Organization f = khoa.get(rnd.nextInt(khoa.size()));
            String fullName = HO[rnd.nextInt(HO.length)] + " "
                    + DEM[rnd.nextInt(DEM.length)] + " "
                    + TEN[rnd.nextInt(TEN.length)];
            students.add(Student.builder()
                    .mssv(String.format("B21DCCN%03d", i + 1))
                    .fullName(fullName)
                    .classCode("D21CQCN" + (rnd.nextInt(9) + 1))
                    .faculty(f)
                    .cohort("K21")
                    .build());
        }
        students = studentRepository.saveAll(students);

        List<User> studentUsers = new ArrayList<>(STUDENT_COUNT);
        for (Student s : students) {
            studentUsers.add(User.builder()
                    .username(s.getMssv().toLowerCase())
                    .passwordHash(hash)
                    .role(Role.STUDENT)
                    .student(s)
                    .enabled(true)
                    .build());
        }
        userRepository.saveAll(studentUsers);

        Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS);
        String[][] eventSpec = {
                {"Hội diễn văn nghệ chào tân sinh viên", "VAN_NGHE", "C3"},
                {"Chiến dịch Mùa hè xanh", "TINH_NGUYEN", "C3"},
                {"Hiến máu nhân đạo đợt 1", "TINH_NGUYEN", "C4"},
                {"Seminar An toàn thông tin", "HOC_THUAT", "C1"},
                {"Giải bóng đá sinh viên", "THE_THAO", "C3"},
        };
        for (int i = 0; i < eventSpec.length; i++) {
            byte[] secret = new byte[32];
            secureRandom.nextBytes(secret);
            eventRepository.save(Event.builder()
                    .org(doan)
                    .title(eventSpec[i][0])
                    .type(eventSpec[i][1])
                    .criteriaCode(eventSpec[i][2])
                    .startAt(base.plus(i, ChronoUnit.DAYS))
                    .endAt(base.plus(i, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                    .location("Hội trường A" + (i + 1))
                    .lat(new BigDecimal("21.0369530"))
                    .lng(new BigDecimal("105.7823800"))
                    .radiusM(150)
                    .capacity(200)
                    .points(5)
                    .secretKey(secret)
                    .status(EventStatus.OPEN)
                    .build());
        }

        log.info("Seed xong: {} tổ chức, {} sinh viên, {} tài khoản, {} sự kiện. Mật khẩu demo: {}",
                orgRepository.count(), studentRepository.count(),
                userRepository.count(), eventRepository.count(), DEMO_PASSWORD);
    }
}
