<br/>
<p align="center">
  <a href="https://github.com/rearth/Oracle-Index">
    <img src="https://github.com/user-attachments/assets/eaeb76b2-3597-4128-96aa-bec9ba02c5c9" alt="Logo" width="80" height="80">
  </a>

<h3 align="center">Oracle-Index</h3>

<div align="center">
  A minecraft fabric / neoforge client-side documentation viewer mod, intended to be used with wikis alongside moddedmc.wiki
  <br/>
  <br/>
  <a href="https://moddedmc.wiki/en/project/oracle-index/docs"><strong>Explore the docs»</strong></a>
  <br/>
  <br/>
  <a href="https://github.com/rearth/Oracle_Index/issues">Report Bug</a>
  .
  <a href="https://github.com/rearth/Oracle_Index/issues">Request Feature</a>
  <br/>
  <br/>
  <br/>

![Downloads](https://img.shields.io/github/downloads/rearth/Oracle-Index/total) ![Stargazers](https://img.shields.io/github/stars/rearth/Oracle-Index?style=social) ![Issues](https://img.shields.io/github/issues/rearth/Oracle-Index) ![License](https://img.shields.io/github/license/rearth/Oracle-Index) ![Discord](https://img.shields.io/discord/1233448016128512082)

</div>

---
<p align="center">
  <img src="https://github.com/user-attachments/assets/6ec59a62-f68a-4a61-908b-cfc4480503b9" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/e2649e0e-4827-46df-8309-adbafe2d1730" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/615fca18-fd3a-4537-b980-d08f4ae0fd60" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/3df07552-5d4a-43d7-8add-25cafc36a21b" />
</p>


---

## About The Project

A simple ingame wiki / documentation viewer, built for neoforge and fabric 1.21. Uses the same formatting / directory layout as https://moddedmc.wiki/,
so mod wikis can be created for both ingame usage and online view without having to rewrite the pages. Support both documentation and content wikis.
If both are found in a wiki, it'll include a button to switch in the navigation panel.

Includes a basic markdown parser for the content, and support for some custom html tags similar to moddedmc.wiki. 
Relevant items can be defined per wiki page, allowing
the users to directly open the relevant wiki pages from the items tooltip.

Features:
- Easy to view ingame documentation.
- No custom books required, the Oracle Index can be opened through either a hotkey or from an item tooltip.
- Semantic Search search, with math expression parsing.
- Smooth scrolling, modern minecraft-y design elements.
- Compatibility for documentations created for moddedmc.wiki.
- Cross-Linking between pages and items.
- Responsive layouts.

## Built With / Depends on:
- Owo lib (for all the GUIs, config, and much more)
- Architectury

## About the Search:
- Langchain4j using all-MiniLm-L6-V2-q embedding model for search embedding vector generation.
- The search query is transformed using the above sentance transformer embedding model. The resulting embedding vector is
then compared to the embeddings generated for all the wiki entries. This model is around 16mb in size (+ a few MB for the runtime), but allows the search
to understand full sentences and find similar content that match the meaning of the search, not just the keywords.

## Roadmap

See the [open issues](https://github.com/rearth/Oracle-Index/issues) for a list of proposed features (and known issues).

## Contributing

Contributions are what make the open source community such an amazing place to be learn, inspire, and create. Any
contributions you make are **greatly appreciated**.

* If you have suggestions for adding or removing projects, feel free
  to [open an issue](https://github.com/rearth/Oracle-Index/issues/new) to discuss it, or directly create a pull request
  after you edit the *README.md* file with necessary changes.
* Please make sure you check your spelling and grammar.
* Create individual PR for each suggestion.

### Creating A Pull Request

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
