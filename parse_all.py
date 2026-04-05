import requests
from bs4 import BeautifulSoup

# List URL genre yang mau di-scrape
genres = {
    "Action": "https://ssstik.tv/genre/action/",
    "Horror": "https://ssstik.tv/genre/horror/",
    "Drama": "https://ssstik.tv/genre/drama/",
    "Anime": "https://ssstik.tv/genre/anime/",
    "Vivamax": "https://ssstik.tv/genre/vivamax/",
    "Drama Korea": "https://ssstik.tv/genre/drama-korea/"
}

for genre_name, url in genres.items():
    print(f"\n===== {genre_name.upper()} =====\n")
    
    try:
        response = requests.get(url)
        response.raise_for_status()
    except requests.RequestException as e:
        print(f"Gagal ambil {genre_name}: {e}")
        continue

    soup = BeautifulSoup(response.text, "html.parser")
    
    films = soup.find_all("article", class_="item movies") + soup.find_all("article", class_="item tvshows")
    
    if not films:
        print("Tidak ada data film ditemukan!")
        continue

    for film in films:
        title_tag = film.find("h3")
        rating_tag = film.find("div", class_="rating")
        quality_tag = film.find("span", class_="quality")
        link_tag = film.find("a", href=True)

        title = title_tag.text.strip() if title_tag else "Unknown"
        rating = rating_tag.text.strip() if rating_tag else "N/A"
        quality = quality_tag.text.strip() if quality_tag else "N/A"
        link = link_tag['href'].strip() if link_tag else "N/A"

        print(f"Title: {title}")
        print(f"Rating: {rating}")
        print(f"Quality: {quality}")
        print(f"Link: {link}\n")
