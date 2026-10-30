package hu.kerekpar.application.usecase;

/** A felhasználótól származó szöveg biztonságos naplózása (vezérlőkarakterek cseréje, hossz-korlát): naplóhamisítás ellen. */
final class LogText {

    private static final int MAX_LENGTH = 40;

    private LogText() {
    }

    static String printable(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) + "…" : cleaned;
    }
}
