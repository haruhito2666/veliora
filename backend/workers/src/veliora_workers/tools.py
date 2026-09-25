"""Web search tool: Tavily when TAVILY_API_KEY is set, else DuckDuckGo."""
from __future__ import annotations

import os
from typing import Any

import httpx


class WebSearch:
    """Real web search returning real results (no mock data)."""

    def search(self, query: str, limit: int = 3) -> list[dict[str, str]]:
        key = os.environ.get("TAVILY_API_KEY")
        if key:
            return self._tavily(query, key, limit)
        return self._duckduckgo(query, limit)

    def _tavily(self, query: str, key: str, limit: int) -> list[dict[str, str]]:
        resp = httpx.post(
            "https://api.tavily.com/search",
            json={"api_key": key, "query": query, "max_results": limit},
            timeout=20.0,
        )
        resp.raise_for_status()
        out: list[dict[str, str]] = []
        for r in resp.json().get("results", []):
            out.append({"title": r.get("title", ""), "url": r.get("url", ""), "snippet": r.get("content", "")})
        return out

    def _duckduckgo(self, query: str, limit: int) -> list[dict[str, str]]:
        # DuckDuckGo Instant Answer API — real results, no key needed.
        resp = httpx.get(
            "https://api.duckduckgo.com/",
            params={"q": query, "format": "json", "no_html": 1, "skip_disambig": 1},
            timeout=15.0,
        )
        resp.raise_for_status()
        data: dict[str, Any] = resp.json()
        out: list[dict[str, str]] = []
        if data.get("AbstractText"):
            out.append({"title": data.get("Heading", query), "url": data.get("AbstractURL", ""),
                        "snippet": data["AbstractText"]})
        for r in data.get("RelatedTopics", [])[:limit]:
            if isinstance(r, dict) and r.get("Text"):
                out.append({"title": r.get("Text", "")[:80], "url": r.get("FirstURL", ""),
                            "snippet": r.get("Text", "")})
        return out
