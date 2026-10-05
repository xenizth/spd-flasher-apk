package com.spdflasher;

/** Chipset / brand table with the default load addresses used by the common loaders. */
final class Profiles {
    private Profiles() {}

    static final class Chip {
        final String id;
        final String label;
        final String fdl1;
        final String fdl2;
        final String exec;
        final String execAlt;
        final String[] brands;

        Chip(String id, String label, String fdl1, String fdl2, String exec, String execAlt,
             String... brands) {
            this.id = id;
            this.label = label;
            this.fdl1 = fdl1;
            this.fdl2 = fdl2;
            this.exec = exec;
            this.execAlt = execAlt;
            this.brands = brands;
        }
    }

    /**
     * UMS9230 values match the Spd_dump_termux docs. The others come from community
     * scripts and are only starting points: they stay editable in the Device tab.
     */
    static final Chip[] CHIPS = {
            new Chip("ums9230", "UMS9230", "0x65000800", "0x9efffe00", "0x65015f08", "0x65015f48",
                    "Infinix", "Itel", "Realme", "Tecno"),
            new Chip("sc9863a", "SC9863A", "0x5000", "0x9efffe00", "0x4ee8", "0x4f48",
                    "Itel", "Realme"),
            new Chip("ums512", "UMS512", "0x5500", "0x9efffe00", "0x3ee8", "0x3f48",
                    "Infinix", "Realme"),
            new Chip("ums312", "UMS312", "", "", "", "", "Generic"),
            new Chip("custom", "Custom", "", "", "", "", "Generic"),
    };

    static Chip find(String id) {
        for (Chip c : CHIPS) if (c.id.equals(id)) return c;
        return null;
    }
}
