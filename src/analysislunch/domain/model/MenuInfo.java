package analysislunch.domain.model;

public record MenuInfo(String date, String menu) {
    public MenuInfo {
        if (date == null) date = "날짜 없음";
        if (menu == null) menu = "메뉴 내용 없음";
    }
}
