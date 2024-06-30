package com.sky.dto;

import io.swagger.models.auth.In;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrdersCancelDTO implements Serializable {

    private Integer id;
    //订单取消原因
    private String cancelReason;

}
