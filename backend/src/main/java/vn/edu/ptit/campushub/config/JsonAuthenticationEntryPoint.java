package vn.edu.ptit.campushub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Trả lỗi 401 dạng JSON thay cho thách thức HTTP Basic mặc định.
 *
 * Nếu backend gửi header "WWW-Authenticate: Basic", trình duyệt sẽ tự bật hộp thoại
 * đăng nhập của hệ thống mỗi khi giao diện gọi API lúc chưa đăng nhập. Ứng dụng có
 * màn hình đăng nhập riêng, nên ở đây chỉ trả mã 401 và thông điệp để giao diện hiển thị.
 * Các client gửi sẵn header Authorization (curl -u, giao diện React) vẫn hoạt động bình thường.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String message = exception instanceof BadCredentialsException
                ? "Tên đăng nhập hoặc mật khẩu không đúng"
                : "Vui lòng đăng nhập";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"message\":\"" + message + "\"}");
    }
}
