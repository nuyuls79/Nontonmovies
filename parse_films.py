from bs4 import BeautifulSoup

# Buka file HTML yang sudah kamu download
with open("movies.html", "r", encoding="utf-8") as f:
    html = f.read()

soup = BeautifulSoup(html, "html.parser")

# Semua artikel film
films = soup.find_all("article", class_="item movies")

for film in films:
    title = film.find("h3").text.strip()
    link = film.find("a")["href"]
    img = film.find("img")["src"]
    rating_tag = film.find("div", class_="rating")
    rating = rating_tag.text.strip() if rating_tag else "N/A"
    quality_tag = film.find("span", class_="quality")
    quality = quality_tag.text.strip() if quality_tag else "N/A"
    
    print(f"Title: {title}")
    print(f"Link: {link}")
    print(f"Image: {img}")
    print(f"Rating: {rating}")
    print(f"Quality: {quality}")
    print("-"*40)
