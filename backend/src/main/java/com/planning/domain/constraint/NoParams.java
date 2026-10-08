package com.planning.domain.constraint;

/** Paramètres d'un plugin structurel : il n'y en a pas. */
public record NoParams() {

    public static final NoParams INSTANCE = new NoParams();
}
