package lol.zeo.zcblive.client.clickpack;

import lol.zeo.zcblive.client.ZcbConfig;

public enum ClickType {
    HARD_CLICK,
    HARD_RELEASE,
    CLICK,
    RELEASE,
    SOFT_CLICK,
    SOFT_RELEASE,
    MICRO_CLICK,
    MICRO_RELEASE,
    NONE;

    public static ClickType fromTime(boolean push, double time, ZcbConfig.Timings timings, boolean allowHardClicks) {
        if (time > timings.hard) {
            if (!allowHardClicks) {
                return push ? CLICK : RELEASE;
            }
            return push ? HARD_CLICK : HARD_RELEASE;
        }
        if (time > timings.regular) {
            return push ? CLICK : RELEASE;
        }
        if (time > timings.soft) {
            return push ? SOFT_CLICK : SOFT_RELEASE;
        }
        return push ? MICRO_CLICK : MICRO_RELEASE;
    }

    public ClickType[] preferred() {
        switch (this) {
            case HARD_CLICK:
                return new ClickType[]{HARD_CLICK, CLICK, SOFT_CLICK, MICRO_CLICK, HARD_RELEASE, RELEASE, SOFT_RELEASE, MICRO_RELEASE};
            case HARD_RELEASE:
                return new ClickType[]{HARD_RELEASE, RELEASE, SOFT_RELEASE, MICRO_RELEASE, HARD_CLICK, CLICK, SOFT_CLICK, MICRO_CLICK};
            case CLICK:
                return new ClickType[]{CLICK, HARD_CLICK, SOFT_CLICK, MICRO_CLICK, RELEASE, HARD_RELEASE, SOFT_RELEASE, MICRO_RELEASE};
            case RELEASE:
                return new ClickType[]{RELEASE, HARD_RELEASE, SOFT_RELEASE, MICRO_RELEASE, CLICK, HARD_CLICK, SOFT_CLICK, MICRO_CLICK};
            case SOFT_CLICK:
                return new ClickType[]{SOFT_CLICK, MICRO_CLICK, CLICK, HARD_CLICK, SOFT_RELEASE, MICRO_RELEASE, RELEASE, HARD_RELEASE};
            case SOFT_RELEASE:
                return new ClickType[]{SOFT_RELEASE, MICRO_RELEASE, RELEASE, HARD_RELEASE, SOFT_CLICK, MICRO_CLICK, CLICK, HARD_CLICK};
            case MICRO_CLICK:
                return new ClickType[]{MICRO_CLICK, SOFT_CLICK, CLICK, HARD_CLICK, MICRO_RELEASE, SOFT_RELEASE, RELEASE, HARD_RELEASE};
            case MICRO_RELEASE:
                return new ClickType[]{MICRO_RELEASE, SOFT_RELEASE, RELEASE, HARD_RELEASE, MICRO_CLICK, SOFT_CLICK, CLICK, HARD_CLICK};
            default:
                return new ClickType[]{NONE};
        }
    }
}
