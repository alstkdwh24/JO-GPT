package com.example.jo_gpt_program.gpt.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class GmailService {

    private final GoogleApiService googleApiService;

    // 최근 메일 목록 조회
    public List<String> getRecentEmails(Long memberKey) throws Exception {
        Gmail gmail = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                googleApiService.getCredentials(memberKey)
        ).setApplicationName("jo-gpt").build();

        return gmail.users().messages().list("me")
                .setMaxResults(10L).execute()
                .getMessages().stream()
                .map(m -> {
                    try {
                        Message msg = gmail.users().messages().get("me", m.getId())
                                .setFormat("metadata")
                                .setMetadataHeaders(List.of("Subject"))
                                .execute();

                        return msg.getPayload().getHeaders().stream()
                                .filter(h -> "Subject".equals(h.getName()))
                                .map(MessagePartHeader::getValue)
                                .findFirst().orElse("(제목 없음)");
                    } catch (Exception e) {
                        return "오류";
                    }
                }).toList();
    }

    // 메일 발송 (첨부파일 없을 때)
    public void sendEmail(Long memberKey, String to, String subject, String body) throws GeneralSecurityException, IOException {
        Gmail gmail = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                googleApiService.getCredentials(memberKey)
        ).setApplicationName("jo-gpt").build();

        String rawEmail = "To: " + to + "\r\nSubject: " + subject + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + body;
        String encoded = Base64.getUrlEncoder().encodeToString(rawEmail.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Message message = new Message().setRaw(encoded);
        gmail.users().messages().send("me", message).execute();
    }

    // 메일 발송 (첨부파일 있을 때)
    public void sendEmailWithAttachment(Long memberKey, String to, String subject, String body,
                                        byte[] fileBytes, String fileName, String mimeType) throws Exception {
        Gmail gmail = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                googleApiService.getCredentials(memberKey)
        ).setApplicationName("jo-gpt").build();

        // 텍스트 파트
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body, "utf-8");

        // 첨부파일 파트
        MimeBodyPart attachPart = new MimeBodyPart();
        attachPart.setContent(fileBytes, mimeType);
        attachPart.setFileName(fileName);

        // 멀티파트 조합
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachPart);

        // MimeMessage 생성
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setSubject(subject, "utf-8");
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.TO, to);
        mimeMessage.setContent(multipart);

        // ByteArray로 변환 후 Base64 인코딩
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String encoded = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

        Message message = new Message().setRaw(encoded);
        gmail.users().messages().send("me", message).execute();
    }
}