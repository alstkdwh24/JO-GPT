package com.example.jo_gpt_program.gpt.dto;
// record는 java 16 + 에서 추가된 불변 데이터 전달용 클래스이다.

public record ChatMessageDTO(String role, String content) {
}

// equals, hashCode, toString, getter 메서드가 자동으로 생성된다. (롬복의 @Data 같은 역할)
// equls() 둗 객체의 값이 같은지 비교하기 위해
// hashCode()는 HashMap, HashSet등에서 객체를 빠르게 찾기 위한 숫자 키
// equals()를 오버라이드하면 반드시 hashCode()도 오버라이드해야 함
// toString()는 객체의 내용을 문자열로 표현하는 메서드, 디버깅할 때 유용