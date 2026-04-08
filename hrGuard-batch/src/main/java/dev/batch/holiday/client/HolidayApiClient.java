package dev.batch.holiday.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 한국천문연구원 특일정보 API - 공휴일 조회 (getRestDeInfo)
// 응답 XML에서 isHoliday=Y 항목만 파싱하여 반환
@Slf4j
@Component
public class HolidayApiClient {

    private static final String BASE_URL =
            "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    private static final DateTimeFormatter LOCDATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Value("${holiday.api.service-key}")
    private String serviceKey;

    public List<HolidayItem> fetchHolidays(int year, int month) throws Exception {
        String xmlResponse = callApi(year, month);
        return parseXml(xmlResponse);
    }

    private String callApi(int year, int month) throws Exception {
        String monthStr = String.format("%02d", month);

        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("?").append(URLEncoder.encode("serviceKey", StandardCharsets.UTF_8))
                .append("=").append(serviceKey);
        urlBuilder.append("&").append(URLEncoder.encode("pageNo", StandardCharsets.UTF_8))
                .append("=").append(URLEncoder.encode("1", StandardCharsets.UTF_8));
        urlBuilder.append("&").append(URLEncoder.encode("numOfRows", StandardCharsets.UTF_8))
                .append("=").append(URLEncoder.encode("100", StandardCharsets.UTF_8));
        urlBuilder.append("&").append(URLEncoder.encode("solYear", StandardCharsets.UTF_8))
                .append("=").append(URLEncoder.encode(String.valueOf(year), StandardCharsets.UTF_8));
        urlBuilder.append("&").append(URLEncoder.encode("solMonth", StandardCharsets.UTF_8))
                .append("=").append(URLEncoder.encode(monthStr, StandardCharsets.UTF_8));

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        BufferedReader rd = new BufferedReader(new InputStreamReader(
                responseCode >= 200 && responseCode <= 300
                        ? conn.getInputStream()
                        : conn.getErrorStream(),
                StandardCharsets.UTF_8
        ));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            sb.append(line);
        }
        rd.close();
        conn.disconnect();

        return sb.toString();
    }

    private List<HolidayItem> parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        List<HolidayItem> result = new ArrayList<>();
        NodeList items = doc.getElementsByTagName("item");

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String isHoliday = getTextContent(item, "isHoliday");

            // 공휴일(Y)만 수집, 기념일 등 제외
            if (!"Y".equals(isHoliday)) {
                continue;
            }

            String locdate = getTextContent(item, "locdate");
            String dateName = getTextContent(item, "dateName");
            LocalDate date = LocalDate.parse(locdate, LOCDATE_FORMAT);
            result.add(new HolidayItem(date, dateName));
        }

        return result;
    }

    private String getTextContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }
}
