package com.kce.kmrl.user.dto;

import lombok.Data;

@Data
public class MessageResponse {
    private String message;

	public MessageResponse(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}