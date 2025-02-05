package com.ksptool.ql.biz.model.po;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.util.Date;

@Entity
@Table(name = "app")
@Data
public class AppPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("应用ID")
    private Long id;

    @Comment("用户ID")
    private Long userId;

    @Column(nullable = false, length = 100)
    @Comment("应用名称")
    private String name;

    @Column(nullable = false, length = 100)
    @Comment("应用类型 0:EXE 1:BAT")
    private Integer kind;

    @Column(length = 255)
    @Comment("应用描述")
    private String description;

    @Column(nullable = false, length = 255)
    @Comment("程序路径")
    private String execPath;

    @Column(length = 255)
    @Comment("图标路径")
    private String iconPath;

    @Comment("运行次数")
    private Integer launchCount;

    @Comment("上次运行时间")
    private Date lastLaunchTime;

    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private Date createTime;

    @Column(name = "update_time", nullable = false)
    @Comment("修改时间")
    private Date updateTime;

    @PrePersist
    public void prePersist() {
        createTime = new Date();
        updateTime = new Date();
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = new Date();
    }
}