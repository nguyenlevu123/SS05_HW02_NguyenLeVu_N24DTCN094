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

    // Template System Prompt tĩnh có chứa placeholder {current_date}
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
        // Lấy ngày hiện tại từ hệ thống máy chủ (Định dạng YYYY-MM-DD)
        String currentDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Khởi tạo SystemPromptTemplate và tiêm biến thời gian thực vào prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
        String dynamicSystemPrompt = systemPromptTemplate.createMessage(Map.of("current_date", currentDateStr)).getContent();

        // Thực thi gọi LLM với System Prompt động đã được tiêm ngữ cảnh thời gian
        return this.chatClient.prompt()
                .system(dynamicSystemPrompt)
                .user(message)
                .call()
                .content();
    }
}
