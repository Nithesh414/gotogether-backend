
package com.gotogether.backend.dto;

public class RegisterRequest {

    private String fullName;
    private String mobileNumber;
    private String email;
    private String password;

    private Boolean termsAccepted;
    private Boolean privacyAccepted;

    public String getFullName() { return fullName; }
    public String getMobileNumber() { return mobileNumber; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    public Boolean getTermsAccepted() { return termsAccepted; }
    public Boolean getPrivacyAccepted() { return privacyAccepted; }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTermsAccepted(Boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    public void setPrivacyAccepted(Boolean privacyAccepted) {
        this.privacyAccepted = privacyAccepted;
    }
}
