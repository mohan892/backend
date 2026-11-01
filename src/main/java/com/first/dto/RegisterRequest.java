package com.first.dto;

public class RegisterRequest {
    private String uid;
    private String name;
    private String phone;
    private String gender;
    private String language;
    private String idToken;

    public RegisterRequest() {}

    public RegisterRequest(String uid, String name, String phone, String gender, String language, String idToken) {
        this.uid = uid;
        this.name = name;
        this.phone = phone;
        this.gender = gender;
        this.language = language;
        this.idToken = idToken;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
