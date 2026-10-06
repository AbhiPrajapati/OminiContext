package com.omnicontext.dto;

import jakarta.validation.constraints.NotBlank;

public class AddNoteRequest {

    private String authorName = "Collaborator";

    @NotBlank(message = "Note content cannot be empty")
    private String note;

    public AddNoteRequest() {
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
