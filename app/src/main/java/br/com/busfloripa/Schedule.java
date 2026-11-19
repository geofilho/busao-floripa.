package br.com.busfloripa;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Cálculos de horário: tipo de dia, feriados, próximas saídas e textos falados. */
public final class Schedule {

    public static final class Arrival implements Comparable<Arrival> {
        public final FenixParser.Trip trip;
        /** Minutos a partir da meia-noite de hoje em que o ônibus passa no seu ponto. */
        public final int at;
        /** Minutos a partir da meia-noite de hoje em que o ônibus sai do ponto de partida. */
        public final int departs;
        Arrival(FenixParser.Trip trip, int departs, int at) { this.trip = trip; this.departs = departs; this.at = at; }
        @Override public int compareTo(Arrival o) { return Integer.compare(at, o.at); }
    }

    /** Tipo de tabela do dia: dias úteis, sábado ou domingos e feriados. */
    public static int dayType(LocalDate d) {
        if (isHoliday(d) || d.getDayOfWeek() == DayOfWeek.SUNDAY) return FenixParser.DOMINGO;
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) return FenixParser.SABADO;
        return FenixParser.UTEIS;
    }

    /** Feriados nacionais fixos, Sexta-feira Santa e aniversário de Florianópolis (23/03). */
    public static boolean isHoliday(LocalDate d) {
        int md = d.getMonthValue() * 100 + d.getDayOfMonth();
        int[] fixed = {101, 323, 421, 501, 907, 1012, 1102, 1115, 1120, 1225};
        for (int f : fixed) if (f == md) return true;
        return d.equals(easter(d.getYear()).minusDays(2));
    }

    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }

    /**
     * Próximas passagens no seu ponto.
     * @param offset minutos entre a saída e o seu ponto
     * @param todayType tipo de tabela para hoje (permite trocar manualmente)
     */
    public static List<Arrival> upcoming(FenixParser.Departure dep, int offset, LocalDateTime now, int todayType, int count) {
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<Arrival> out = new ArrayList<>();
        int yType = dayType(now.toLocalDate().minusDays(1));
        for (FenixParser.Trip t : dep.days.get(yType))
            if (t.min >= 1440 && t.min - 1440 + offset >= nowMin) out.add(new Arrival(t, t.min - 1440, t.min - 1440 + offset));
        for (FenixParser.Trip t : dep.days.get(todayType))
            if (t.min + offset >= nowMin) out.add(new Arrival(t, t.min, t.min + offset));
        if (out.size() < count) {
            int tType = dayType(now.toLocalDate().plusDays(1));
            for (FenixParser.Trip t : dep.days.get(tType)) {
                out.add(new Arrival(t, t.min + 1440, t.min + 1440 + offset));
                if (out.size() >= count + 2) break;
            }
        }
        Collections.sort(out);
        return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;
    }

    // ---------- Textos (pensados para leitura pelo TalkBack) ----------

    public static String clock(int min) {
        int m = ((min % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    public static String inMinutes(int diff) {
        if (diff <= 0) return "agora";
        if (diff == 1) return "em 1 minuto";
        if (diff < 60) return "em " + diff + " minutos";
        int h = diff / 60, m = diff % 60;
        String hs = h == 1 ? "1 hora" : h + " horas";
        if (m == 0) return "em " + hs;
        return "em " + hs + " e " + (m == 1 ? "1 minuto" : m + " minutos");
    }

    public static String flagsText(String flags) {
        List<String> p = new ArrayList<>();
        if (flags.contains("E")) p.add("com elevador");
        if (flags.contains("M")) p.add("meia viagem");
        if (flags.contains("R")) p.add("recolhe à garagem");
        if (flags.contains("*")) p.add("previsto, depende do trânsito");
        return String.join(", ", p);
    }

    /** Diz se a saída fica no centro (então o ônibus vai sentido bairro). */
    public static boolean isCentro(String depName) {
        String f = FenixParser.fold(depName);
        return f.startsWith("ticen") || f.contains(" ticen") || f.startsWith("centro") || f.contains("terminal centro");
    }

    /** Versão curta para a lista: "sentido centro", "sentido bairro" ou "saída X". */
    public static String sentidoShort(FenixParser.Timetable tt, int idx) {
        String full = sentidoLabel(tt, idx);
        if (full.startsWith("Sentido centro")) return "sentido centro";
        if (full.startsWith("Sentido bairro")) return "sentido bairro";
        return "saída " + tt.departures.get(idx).name;
    }

    /** Rótulo amigável de uma saída: "Sentido bairro, saída TICEN ...". */
    public static String sentidoLabel(FenixParser.Timetable tt, int idx) {
        String name = tt.departures.get(idx).name;
        boolean centro = isCentro(name);
        boolean otherCentro = false;
        for (int i = 0; i < tt.departures.size(); i++)
            if (i != idx && isCentro(tt.departures.get(i).name)) otherCentro = true;
        if (centro && !otherCentro) return "Sentido bairro, saída " + name;
        if (!centro && otherCentro) return "Sentido centro, saída " + name;
        return "Saída " + name;
    }
}
