package com.ksptool.ql.biz.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunAppDto {

    @NotNull
    private Long appId;

}
