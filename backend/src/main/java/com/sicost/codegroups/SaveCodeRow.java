package com.sicost.codegroups;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/** JSON 객체를 보존하여 생략·명시 null·값의 세 상태를 구별한다. */
public final class SaveCodeRow {
    private final JsonNode fields;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SaveCodeRow(JsonNode fields) {
        this.fields = fields;
    }

    public JsonNode fields() {
        return fields;
    }
}
