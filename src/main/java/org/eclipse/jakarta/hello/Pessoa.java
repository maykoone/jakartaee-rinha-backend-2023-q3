package org.eclipse.jakarta.hello;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Pessoa(UUID id, String apelido, String nome, LocalDate nascimento, String[] stack) {
    public Pessoa {
        Objects.requireNonNull(apelido, () -> "Apelido não pode ser nulo");
        Objects.requireNonNull(nome, () -> "Nome não pode ser nulo");
        Objects.requireNonNull(nascimento, () -> "Nascimento não pode ser nulo");

        if (apelido.length() > 32)
            throw new IllegalArgumentException("apelido pode ter até 32 caracteres");

        if (nome.length() > 100)
            throw new IllegalArgumentException("nome pode ter até 100 caracteres");

        if (stack != null) {
            for (String item : stack) {
                if (item.length() > 32)
                    throw new IllegalArgumentException("uma stack pode ter até 32 caracteres");
            }
        }
    }
}
