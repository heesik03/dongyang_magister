package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.converter.HistoryConverter;
import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.domain.history.error.HistoryErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Component
public class HeritageClient implements HistorySource {
    private static final String SOURCE_ID = "[0-9]{2}-[0-9]{13}-[0-9A-Z]{2}";
    private final RestClient restClient;

    public HeritageClient() {
        this(HistoryHttp.client("https://www.khs.go.kr/cha"));
    }

    HeritageClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<HistoryCandidate> candidates(String query) {
        Element result = fetch("/SearchKindOpenapiList.do?ccbaMnm1={name}&pageUnit=100", query);
        List<Element> items = childElements(result, "item");
        String totalCnt = childText(result, "totalCnt");
        if (!totalCnt.matches("[0-9]+") || (items.isEmpty() && !"0".equals(totalCnt))) {
            throw invalidResponse();
        }
        List<HistoryCandidate> candidates = new ArrayList<>();
        for (Element item : items) {
            String id = sourceId(item);
            String title = childText(item, "ccbaMnm1");
            if (!id.matches(SOURCE_ID) || title.isBlank()) {
                throw invalidResponse();
            }
            String description =
                    (childText(item, "ccmaName")
                                    + " "
                                    + childText(item, "ccbaCtcdNm")
                                    + " "
                                    + childText(item, "ccsiName"))
                            .strip();
            candidates.add(HistoryConverter.toCandidate(id, title, description));
        }
        return HistorySource.ranked(candidates, query);
    }

    @Override
    public HistoryEvidence evidence(String sourceId, List<String> keywords) {
        if (!sourceId.matches(SOURCE_ID)) {
            throw new CustomException(HistoryErrorCode.INVALID_SOURCE_ID);
        }
        String[] codes = sourceId.split("-");
        Element result =
                fetch(
                        "/SearchKindOpenapiDt.do?ccbaKdcd={kind}&ccbaAsno={number}&ccbaCtcd={region}",
                        codes[0],
                        codes[1],
                        codes[2]);
        List<Element> items = childElements(result, "item");
        if (items.isEmpty() || !sourceId.equals(sourceId(result))) {
            throw invalidResponse();
        }
        Element item = items.getFirst();
        String title = childText(item, "ccbaMnm1");
        String portalId = childText(result, "ccbaCpno");
        if (portalId.isEmpty()) {
            portalId = childText(item, "ccbaCpno");
        }
        if (title.isEmpty() && portalId.isEmpty()) {
            throw new CustomException(HistoryErrorCode.SOURCE_NOT_FOUND);
        }
        if (title.isEmpty() || !portalId.matches("[0-9]+")) {
            throw invalidResponse();
        }
        URI url =
                URI.create(
                        "https://www.heritage.go.kr/heri/cul/culSelectDetail.do?ccbaAsno="
                                + codes[1]
                                + "&ccbaCpno="
                                + portalId);
        return HistorySource.excerpt(
                "국가유산청", sourceId, title, url, keywords, childText(item, "content"));
    }

    private static String sourceId(Element element) {
        return String.join(
                "-",
                childText(element, "ccbaKdcd"),
                childText(element, "ccbaAsno"),
                childText(element, "ccbaCtcd"));
    }

    /** 하위 태그 이름 충돌을 막기 위해 직계 자식 엘리먼트만 탐색한다. */
    private static List<Element> childElements(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        List<Element> matched = new ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                matched.add(element);
            }
        }
        return matched;
    }

    private static String childText(Element parent, String name) {
        List<Element> children = childElements(parent, name);
        return children.isEmpty() ? "" : children.getFirst().getTextContent().strip();
    }

    private Element fetch(String path, Object... values) {
        byte[] xml = HistoryHttp.read(restClient.get().uri(path, values));
        try {
            DocumentBuilderFactory parser = DocumentBuilderFactory.newDefaultInstance();
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root =
                    parser.newDocumentBuilder()
                            .parse(new ByteArrayInputStream(xml))
                            .getDocumentElement();
            if (!"result".equals(root.getTagName())) {
                throw invalidResponse();
            }
            return root;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw invalidResponse();
        }
    }

    private static CustomException invalidResponse() {
        return new CustomException(HistoryErrorCode.PROVIDER_LOOKUP_FAILED);
    }
}
