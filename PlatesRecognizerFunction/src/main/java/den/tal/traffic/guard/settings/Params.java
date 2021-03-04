package den.tal.traffic.guard.settings;

import lombok.Getter;

public enum Params {
    FORMAT_NAME("jpg"),
    CONTENT_TYPE("image/jpeg");

    private String value;

    Params(String value) {
        this.value = value;
    }


    @Override
    public String toString() {

        return value;
    }
}
