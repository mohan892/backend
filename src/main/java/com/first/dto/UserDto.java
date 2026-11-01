package com.first.dto;

public class UserDto {
    private String uid;
    private String name;
    private String phone;
    private String gender;
    private String language;
    private String status;
    private String avatar;
    private String age;
    private boolean verified;
    private String rate;

    public UserDto() {}

    public UserDto(String uid, String name, String phone, String gender, String language, String status, String avatar, String age, boolean verified, String rate) {
        this.uid = uid;
        this.name = name;
        this.phone = phone;
        this.gender = gender;
        this.language = language;
        this.status = status;
        this.avatar = avatar;
        this.age = age;
        this.verified = verified;
        this.rate = rate;
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getRate() { return rate; }
    public void setRate(String rate) { this.rate = rate; }
}
