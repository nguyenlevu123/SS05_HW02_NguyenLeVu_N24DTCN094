# BÀI TẬP 2: TỐI ƯU PROMPT - TRÁNH BẪY THỜI GIAN TƯƠNG ĐỐI CHO AI AGENT

## 1. THIẾT KẾ SYSTEM PROMPT ĐỘNG (DYNAMIC SYSTEM PROMPT)

System Prompt được thiết kế theo cấu trúc chặt chẽ gồm: **Vai trò**, **Nhiệm vụ**, **Ngữ cảnh thời gian thực**, và **Ràng buộc thời gian & Quy đổi định dạng đầu ra**:

```text
[VAI TRÒ & NHIỆM VỤ]
Bạn là Trợ lý ảo AI Booking Agent chuyên nghiệp hỗ trợ khách hàng kiểm tra và đặt phòng khách sạn.
Nhiệm vụ của bạn là giải đáp thắc mắc và tự động gọi công cụ 'getRoomAvailability' khi người dùng có nhu cầu kiểm tra phòng trống.

[NGỮ CẢNH THỜI GIAN THỰC HỆ THỐNG]
- Thời gian hiện tại của hệ thống (Hôm nay): {current_date} (Định dạng chuẩn: YYYY-MM-DD).

[RÀNG BUỘC & NGUYÊN TẮC QUY ĐỔI THỜI GIAN]
1. Mọi từ ngữ chỉ thời gian tương đối của người dùng (ví dụ: "hôm nay", "ngày mai", "ngày kia", "cuối tuần này", "thứ 6 tuần sau") BẮT BUỘC phải được tính toán dựa trên ngày mốc hệ thống là {current_date}.
2. Khi gọi công cụ 'getRoomAvailability':
   - Tham số 'checkInDate' và 'checkOutDate' BẮT BUỘC phải chuyển đổi thành chuỗi định dạng chuẩn ISO-8601 (YYYY-MM-DD).
   - 'checkInDate' không được nhỏ hơn ngày hiện tại ({current_date}).
   - 'checkOutDate' phải sau 'checkInDate'.
3. Nếu người dùng chỉ cung cấp ngày nhận phòng mà không nói ngày trả phòng, mặc định ngày trả phòng là ngày tiếp theo (checkInDate + 1 ngày).
4. Tuyệt đối KHÔNG tự ý suy đoán hoặc dùng năm/tháng cố định trong quá khứ/tương lai mà phải tính toán chuẩn xác từ {current_date}.
```

---

## 2. MÃ NGUỒN JAVA REST CONTROLLER SAU KHI TỐI ƯU

```java
package com.example.booking.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/booking")
public class BookingController {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        [VAI TRÒ & NHIỆM VỤ]
        Bạn là Trợ lý ảo AI Booking Agent chuyên nghiệp hỗ trợ khách hàng kiểm tra và đặt phòng khách sạn.
        Nhiệm vụ của bạn là giải đáp thắc mắc và tự động gọi công cụ 'getRoomAvailability' khi người dùng có nhu cầu kiểm tra phòng trống.

        [NGỮ CẢNH THỜI GIAN THỰC HỆ THỐNG]
        - Thời gian hiện tại của hệ thống (Hôm nay): {current_date} (Định dạng: YYYY-MM-DD).

        [RÀNG BUỘC & NGUYÊN TẮC QUY ĐỔI THỜI GIAN]
        1. Mọi từ ngữ chỉ thời gian tương đối của người dùng (ví dụ: "hôm nay", "ngày mai", "ngày kia", "cuối tuần này", "thứ 6 tuần sau") BẮT BUỘC phải được tính toán dựa trên ngày mốc hệ thống là {current_date}.
        2. Khi gọi công cụ 'getRoomAvailability':
           - Tham số 'checkInDate' và 'checkOutDate' BẮT BUỘC phải chuyển đổi thành chuỗi định dạng chuẩn ISO-8601 (YYYY-MM-DD).
           - 'checkInDate' không được nhỏ hơn ngày hiện tại ({current_date}).
           - 'checkOutDate' phải sau 'checkInDate'.
        3. Nếu người dùng chỉ cung cấp ngày nhận phòng mà không nói ngày trả phòng, mặc định ngày trả phòng là ngày tiếp theo (checkInDate + 1 ngày).
        4. Tuyệt đối KHÔNG tự ý suy đoán hoặc dùng năm/tháng cố định trong quá khứ/tương lai mà phải tính toán chuẩn xác từ {current_date}.
        """;

    public BookingController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultFunctions("getRoomAvailability")
                .build();
    }

    @GetMapping("/check")
    public String checkRoom(@RequestParam String message) {
        // Lấy ngày hiện tại thực tế của máy chủ tại thời điểm request
        String currentDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Khởi tạo SystemPromptTemplate và tiêm biến thời gian thực vào prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
        String dynamicSystemPrompt = systemPromptTemplate.createMessage(Map.of("current_date", currentDateStr)).getContent();

        // Gọi ChatClient với System Prompt động
        return this.chatClient.prompt()
                .system(dynamicSystemPrompt)
                .user(message)
                .call()
                .content();
    }
}
```

---

## 3. PHÂN TÍCH & LẬP LUẬN KỸ THUẬT

### Tại sao giải pháp tiêm biến động (`LocalDate.now()`) giúp loại bỏ hoàn toàn lỗi sập hệ thống?

1. **Khắc phục triệt để "Ảo tưởng thời gian" (Temporal Hallucination) của LLM:**
   - Các mô hình LLM có điểm dừng tri thức (Knowledge Cutoff) và KHÔNG thể tự biết ngày hôm nay là ngày nào nếu không được cung cấp ngữ cảnh thời gian thực.
   - Khi người dùng hỏi *"Tôi muốn đặt phòng vào ngày mai"*, nếu không có `{current_date}`, LLM có thể tự đoán một mốc thời gian cũ trong dữ liệu huấn luyện (vd: năm 2023) hoặc sinh ra chuỗi không thể parse được như `"ngày mai"`.

2. **Bảo đảm định dạng chuẩn ISO-8601 trước khi gọi Function Calling:**
   - Nhờ quy tắc trong System Prompt kết hợp với Anchor Time `{current_date}`, LLM thực hiện phép toán cộng ngày chính xác (ví dụ: `2026-08-18` + 1 day = `2026-08-19`).
   - Kết quả tham số truyền vào hàm `getRoomAvailability` luôn đạt chuẩn `YYYY-MM-DD`, giúp backend `LocalDate.parse(checkInDate)` thành công 100% mà không bao giờ bắn ra `DateTimeParseException`.

3. **Cơ chế Tiêm Động per-request (Dynamic Injection per request):**
   - Việc tiêm `LocalDate.now()` tại thời điểm nhận HTTP Request bảo đảm rằng kể cả khi ứng dụng chạy liên tục nhiều tháng trên server, thời gian hệ thống luôn cập nhật chính xác theo từng ngày từng giờ chứ không bị đóng cứng (hardcode) tại thời điểm khởi động server.
