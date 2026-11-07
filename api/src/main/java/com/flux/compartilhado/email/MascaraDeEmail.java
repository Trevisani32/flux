package com.flux.compartilhado.email;

public final class MascaraDeEmail {

    private MascaraDeEmail() {
    }

    /** "dev.novo@exemplo.local" vira "d•••@exemplo.local". */
    public static String mascarar(String email) {
        int arroba = email == null ? -1 : email.indexOf('@');
        if (arroba <= 0) {
            return "•••";
        }
        return email.charAt(0) + "•••" + email.substring(arroba);
    }
}
