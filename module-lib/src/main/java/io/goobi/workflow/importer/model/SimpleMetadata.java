package io.goobi.workflow.importer.model;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleMetadata {

    @JacksonXmlProperty(isAttribute = true)
    private String label;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private String valueURI;

    @JacksonXmlProperty(isAttribute = true)
    private String doctype;

    @JacksonXmlProperty(isAttribute = true)
    private String authority;

    @JacksonXmlProperty(isAttribute = true)
    private String authorityURI;

    @JacksonXmlText
    private String value;

    /**
     * add authority information based on just one url
     * 
     * @param authorityEntryUrl
     */
    public void analyzeAuthority(String authorityEntryUrl) {
        authority = null;
        authorityURI = null;
        valueURI = null;

        // split authority information
        if (StringUtils.isNotBlank(authorityEntryUrl)) {
            if (authorityEntryUrl.contains("d-nb.info")) {
                authority = "gnd";
            }
            if (authorityEntryUrl.contains("/")) {
                authorityURI = authorityEntryUrl.substring(0, authorityEntryUrl.lastIndexOf("/") + 1);
                valueURI = authorityEntryUrl.substring(authorityEntryUrl.lastIndexOf("/") + 1);
            }
        }
    }
}