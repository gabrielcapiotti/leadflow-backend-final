package com.leadflow.backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public class GrowthPoint {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate date;
    private final long value;

    public GrowthPoint(LocalDate date, long value) {
        this.date = date;
        this.value = value;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getValue() {
        return value;
    }
}
