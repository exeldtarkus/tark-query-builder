package com.tark.query.builder.sql;

public class UserParamsDto {
    private Integer minId;
    private Integer maxId;
    private String name;

    public UserParamsDto() {}

    public UserParamsDto(Integer minId, Integer maxId) {
        this.minId = minId;
        this.maxId = maxId;
    }

    public UserParamsDto(String name) {
        this.name = name;
    }

    public Integer getMinId() { return minId; }
    public void setMinId(Integer minId) { this.minId = minId; }

    public Integer getMaxId() { return maxId; }
    public void setMaxId(Integer maxId) { this.maxId = maxId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
