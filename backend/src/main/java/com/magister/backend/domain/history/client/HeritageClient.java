package com.magister.backend.domain.history.client;

import com.magister.backend.domain.history.dto.HistoryCandidate;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class HeritageClient implements HistorySource {
    private final RestClient restClient;
    private static final String SOURCE_ID = "[0-9]{2}-[0-9]{13}-[0-9A-Z]{2}";

    public HeritageClient() {
        this(HistoryHttp.client("https://www.khs.go.kr/cha"));
    }

    HeritageClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<HistoryCandidate> candidates(String query) {
        Element result = fetch("/SearchKindOpenapiList.do?ccbaMnm1={name}&pageUnit=100", query);
        NodeList items = result.getElementsByTagName("item");
        if (!field(result, "totalCnt").matches("[0-9]+")
                || (items.getLength() == 0 && !"0".equals(field(result, "totalCnt")))) {
            throw invalidResponse();
        }
        List<HistoryCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            String id = sourceId(item);
            String title = field(item, "ccbaMnm1");
            if (!id.matches(SOURCE_ID) || title.isBlank()) {
                throw invalidResponse();
            }
            candidates.add(new HistoryCandidate(id, title,
                    (field(item, "ccmaName") + " " + field(item, "ccbaCtcdNm") + " " + field(item, "ccsiName")).strip()));
        }
        return HistorySource.ranked(candidates, query);
    }

    @Override
    public HistoryEvidence evidence(String sourceId, List<String> keywords) {
        if (!sourceId.matches(SOURCE_ID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String[] codes = sourceId.split("-");
        Element result = fetch("/SearchKindOpenapiDt.do?ccbaKdcd={kind}&ccbaAsno={number}&ccbaCtcd={region}",
                codes[0], codes[1], codes[2]);
        Element item = (Element) result.getElementsByTagName("item").item(0);
        if (item == null || !sourceId.equals(sourceId(result))) {
            throw invalidResponse();
        }
        String title = field(item, "ccbaMnm1");
        String portalId = field(result, "ccbaCpno");
        if (title.isEmpty() && portalId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (title.isEmpty() || !portalId.matches("[0-9]+")) {
            throw invalidResponse();
        }
        URI url = URI.create("https://www.heritage.go.kr/heri/cul/culSelectDetail.do?ccbaAsno="
                + codes[1] + "&ccbaCpno=" + portalId);
        return HistorySource.excerpt("국가유산청", sourceId, title, url, keywords, field(item, "content"));
    }

    private static String sourceId(Element item) {
        return String.join("-", field(item, "ccbaKdcd"), field(item, "ccbaAsno"), field(item, "ccbaCtcd"));
    }

    private static String field(Element element, String name) {
        NodeList children = element.getElementsByTagName(name);
        return children.getLength() == 0 ? "" : children.item(0).getTextContent().strip();
    }

    private Element fetch(String path, Object... values) {
        byte[] xml = HistoryHttp.read(restClient.get().uri(path, values));
        try {
            DocumentBuilderFactory parser = DocumentBuilderFactory.newDefaultInstance();
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = parser.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
            if (!"result".equals(root.getTagName())) {
                throw invalidResponse();
            }
            return root;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw invalidResponse();
        }
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY);
    }
}
