# Auralis TV product website

This is a dependency-free static website for Auralis TV. It sits in `website/` so it can be hosted separately from the Android launcher code.

## Design direction

The Apple TV product pages were reviewed as a reference for presentation, not as a visual template. The strongest ideas are their pacing, oversized type, full-bleed imagery, short editorial chapters, and product shown in context. This page adapts those ideas to Auralis’s own darker palette and Android TV use case, with original artwork and a CSS-built interface preview. The product claims stay specific to what the app currently does.

## Preview locally

From the repository root:

```sh
python3 -m http.server 8000 --directory website
```

Then open `http://localhost:8000`.

## Hosting

The page can be published by any static host. The `website/` directory is the site root. Once the project has a domain, configure that domain in the chosen host and add the host-provided DNS records. The site intentionally does not include a `CNAME` file yet because no domain has been chosen.

For GitHub Pages, a small workflow can publish the contents of `website/` as a Pages artifact. Enable Pages for the repository and add the domain after its DNS is configured; do not use the Android APK workflow to publish the site.

## Product claims

The page describes the current Android implementation: installed-app discovery and launch, local per-profile watchlists and launcher history, a limited film-discovery catalogue, title details and service search links, device speech-recognizer voice search, Control Center, and an ambient clock/artwork screen.

It states that Auralis does not stream titles, provide a universal playback-progress feed, sync watchlists across devices, or guarantee service availability. The interface preview is illustrative; installed apps, catalogue data, voice recognition, and default-launcher behavior depend on the Android TV device and its services.

## Motion and accessibility

The website uses transforms and opacity for entrance and hover movement, reveals content once as it enters view, and honors `prefers-reduced-motion`. It does not promise a fixed frame rate: actual refresh rate and animation smoothness depend on the viewer's display, browser, and graphics hardware.
