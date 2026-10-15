# Licznik K/M

Aplikacja na Samsung Galaxy A55 do liczenia kobiet i mężczyzn.

## Obsługa
- **+** nad liczbą dodaje 1, **−** pod liczbą odejmuje 1 (licznik nie spada poniżej 0).
- **Zapisz** dopisuje wiersz do pliku `Pobrane/LicznikKM/licznik_zapisy.csv`, który otwiera się w Excelu.
  Starsze wiersze nie są nadpisywane.
- **Od nowa** po potwierdzeniu zapisuje wiersz „KONIEC SESJI”, dodaje pustą linię jako przerwę i zeruje liczniki.
- Każdy wiersz zaczyna się od daty startu (pierwsze kliknięcie „+” w sesji) i daty końca (moment zapisu):
  ```
  Start;Koniec;Kobiety;Mężczyźni;Razem;Uwagi
  2026-10-16 09:00:12;2026-10-16 10:15:40;12;9;21;zapis
  2026-10-16 09:00:12;2026-10-16 11:02:03;25;18;43;KONIEC SESJI

  2026-10-16 13:30:05;2026-10-16 14:00:00;7;4;11;KONIEC SESJI
  ```
- Aktualne liczby zapisują się automatycznie po każdym kliknięciu, więc nie znikną po zamknięciu aplikacji ani po restarcie telefonu.
- **Liczenie przyciskami głośności** (przełącznik): głośniej = kobieta +1, ciszej = mężczyzna +1.
  Działa też z zablokowanym telefonem w kieszeni. Bardzo słaba wibracja: 1 stuknięcie = kobieta, 2 = mężczyzna.
  Siłę ustawia się w `Feedback.kt` (`STRENGTH`).

## Instalacja na telefonie
1. Na telefonie otwórz stronę repozytorium, przejdź do **Releases** i pobierz `LicznikKM.apk`.
2. Otwórz pobrany plik i zgódź się na „Instalowanie nieznanych aplikacji” dla przeglądarki lub aplikacji Moje pliki.
3. Przy pierwszym włączeniu liczenia przyciskami zezwól na powiadomienia. Potem kliknij
   „Zezwól na działanie w tle” i wybierz **Bez ograniczeń**, żeby One UI nie usypiało aplikacji.

Nową wersję instalujesz tak samo, na starą. Dane zostają, bo aplikacja jest zawsze podpisana tym samym kluczem (`app/licznik.p12`).

## Ograniczenia
- Gdy włączone jest liczenie przyciskami, nie zmieniają one głośności. W trakcie rozmowy telefonicznej regulują głośność rozmowy.
- Nie słuchaj muzyki w innej aplikacji podczas liczenia, bo przyciski mogą wtedy sterować muzyką.
- Po restarcie telefonu otwórz aplikację raz, żeby znowu działało liczenie przy zablokowanym ekranie.
