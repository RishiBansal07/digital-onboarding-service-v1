package com.randombank.onboarding.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "customers", uniqueConstraints =
        @UniqueConstraint(name = "uk_customers_username", columnNames = "username"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private String password;

    @Setter
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Account account;

    public Customer(String fullName, String address, String username, LocalDate dateOfBirth, String countryCode, String password) {
        this.fullName = fullName;
        this.address = address;
        this.username = username;
        this.dateOfBirth = dateOfBirth;
        this.countryCode = countryCode;
        this.password = password;
    }
}
