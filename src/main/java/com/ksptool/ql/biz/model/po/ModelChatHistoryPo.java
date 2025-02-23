package com.ksptool.ql.biz.model.po;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.util.Date;

@Data
@Entity
@Table(name = "model_chat_history")
public class ModelChatHistoryPo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Comment("关联的会话")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ModelChatThreadPo thread;
    
    @Comment("用户ID")
    @Column(nullable = false)
    private Long userId;
    
    @Comment("角色 user-用户 assistant-AI助手")
    @Column(length = 20, nullable = false)
    private String role;
    
    @Comment("消息内容")
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Comment("创建时间")
    @Column(nullable = false)
    private Date createTime;
    
    @Comment("更新时间")
    @Column(nullable = false)
    private Date updateTime;
    
    @PrePersist
    protected void onCreate() {
        createTime = new Date();
        updateTime = new Date();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = new Date();
    }
} 