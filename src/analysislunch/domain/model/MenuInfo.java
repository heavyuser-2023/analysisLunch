package analysislunch.domain.model;

/**
 * 구내식당 메뉴 정보를 담는 불변 레코드.
 *
 * @param date 메뉴 날짜 (예: "2026-02-18")
 * @param menu 메뉴 내용 (쉼표로 구분된 메뉴 목록)
 */
public record MenuInfo(String date, String menu) {

    private static final String DEFAULT_DATE = "날짜 없음";
    private static final String DEFAULT_MENU = "메뉴 내용 없음";

    /**
     * compact constructor: null 값을 기본값으로 대체합니다.
     */
    public MenuInfo {
        if (date == null) {
            date = DEFAULT_DATE;
        }
        if (menu == null) {
            menu = DEFAULT_MENU;
        }
    }
}
