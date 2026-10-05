package com.example.entitycom.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

@Converter
public class JsonObjectConverter implements AttributeConverter<JSONObject, String> {
    @Override
    public String convertToDatabaseColumn(JSONObject jsonObject) {
        return (jsonObject == null) ? null : jsonObject.toJSONString();
    }

    @Override
    public JSONObject convertToEntityAttribute(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return (JSONObject) new JSONParser(JSONParser.MODE_PERMISSIVE).parse(s);
        } catch (ParseException e) {
            throw new RuntimeException("JSON parsing error", e);
        }

    }
}
