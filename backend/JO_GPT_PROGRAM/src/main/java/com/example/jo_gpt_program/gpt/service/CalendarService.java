package com.example.jo_gpt_program.gpt.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class CalendarService {

    private final GoogleApiService googleApiService;

    // 일정 목록 조회
    public List<String> getEvents(Long memberKey) throws Exception {
        // 캘린더 객체
        Calendar calendar = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), // Google 서버랑 안전하게 통신할수 있는 도로를 만들고 HTTP 통신 객체를 생성
                GsonFactory.getDefaultInstance(), // 통신끼리는 JSON으로 통신을 하는데 그걸 JAVA 객체로 변환해주는 것
                googleApiService.getCredentials(memberKey) // memberKey로 db에서 해당 유저의 accessToken을 꺼내서 Google API 인증 객체로 반들어 반환
        ).setApplicationName("jo-gpt").build(); // 나 서버한데 jo-gpt앱이야 라고 이름표 달아달라는 역할 그리고 캘린도 객체 생성 다 끝나면

        return calendar.events().list("primary")
                .setMaxResults(10) // 최대 10개의 일정
                .setOrderBy("startTime") // 시작한 순서대로
                .setSingleEvents(true)  // 반복이벤트를 개별 이벤트로 풀어서 가져옴
                .execute() // api 요청을 실제로 서버에 보내고 결과를 받아오는 메서드
                .getItems().stream() // execute 로 요청을 보냄 그러면 Event 객체 반환 List<Event> 반환
                .map(e -> e.getSummary() + " | " + e.getStart().getDateTime())
                .toList();
    }

    // 일정 생성
    public void createEvent(Long memberKey, String title, String startDateTime, String endDateTime) throws Exception {
        Calendar calendar = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), // HTTP 통신을 위한 안전한 전송 객체를 생성합니다.
                GsonFactory.getDefaultInstance(),  // JSON 데이터를 직렬화 / 역직렬화하기 위한 JsonFactory를 제공합니다.
                googleApiService.getCredentials(memberKey) // 인증 자격 증명을 가져옵니다.


        ).setApplicationName("jo-gpt").build(); // Google 서비스에 jo-gpt 앱으로 이름표를 만듭니다.
        Event event = new Event()  // 빈 일정 객체 하나 만들어줘
                .setSummary(title) // 일정 제목 설정
                .setStart(new EventDateTime().setDateTime(new DateTime(startDateTime))) // 일정 시작 시간 마감 시간 설정
                .setEnd(new EventDateTime().setDateTime(new DateTime(endDateTime)));
        calendar.events().insert("primary", event) // 캘린더의 이벤트 기능을 사용하면서 위에서 만든 일정 객체 삽입
                .execute(); // 실제로 Google 서버에 전송
    }
}
