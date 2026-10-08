package com.planning.domain.model;

/**
 * L'invariant le plus fragile du système (§7.2).
 *
 * <p>{@code DISCOURAGED} doit avoir {@code allowed = true} : sinon un créneau
 * découragé déclencherait aussi la contrainte dure et deviendrait de fait
 * interdit. Le rendre déclaratif ici — plutôt que par un {@code if} enfoui dans
 * la boucle de compilation — permet de le lire en trois lignes.
 */
public enum Effect {

    AVAILABLE(true, false),
    DISCOURAGED(true, true),
    UNAVAILABLE(false, false);

    private final boolean allowed;
    private final boolean discouraged;

    Effect(boolean allowed, boolean discouraged) {
        this.allowed = allowed;
        this.discouraged = discouraged;
    }

    public boolean allowed() {
        return allowed;
    }

    public boolean discouraged() {
        return discouraged;
    }
}
