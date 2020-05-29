package com.githubApp.model;

import java.io.Serializable;

public class Comment implements Serializable {

    private String body;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
