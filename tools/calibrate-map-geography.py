"""Reproduce node-only geography corrections and generate coordinate QA plots.

The illustrated world map has no CRS. Its manually reviewed pixel anchors are
separate from approximate geographic reference coordinates. Regional schematic
maps use a bounded linear lon/lat layout. No road, ID or travel time is changed.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def coordinates(map_id, place):
    if map_id == "world_72":
        return round(place[2] / 1920, 6), round(1 - place[3] / 1280, 6)
    west, east, south, north = ((98, 125, 19, 43) if map_id == "china_42"
                               else (113, 122, 30, 39))
    lon, lat = place[:2]
    return round(.06 + .88 * (lon - west) / (east - west), 6), round(.06 + .88 * (lat - south) / (north - south), 6)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--root", type=Path, default=ROOT, help="Target checkout; only map node coordinates are updated")
    parser.add_argument("--plots", type=Path)
    parser.add_argument("--check-art", action="store_true", help="Check world anchors against overview and detail tiles for blue water")
    args = parser.parse_args()
    path = args.root / "assets/data/maps/maps.json"
    document = json.loads(path.read_text(encoding="utf-8"))
    places = json.loads((ROOT / "tools/map-geography.json").read_text(encoding="utf-8"))["places"]
    count = 0
    for world in document["maps"]:
        ids = {node["cityId"] for node in world["nodes"]}
        assert len(ids) == len(world["nodes"])
        for road in world["connections"]:
            assert road["fromCityId"] in ids and road["toCityId"] in ids
        for node in world["nodes"]:
            x, y = coordinates(world["id"], places[node["cityId"]])
            assert 0 < x < 1 and 0 < y < 1, node
            if args.apply:
                node["x"], node["y"] = x, y
            else:
                assert abs(node["x"] - x) < 1e-6 and abs(node["y"] - y) < 1e-6, node
            count += 1
    if args.apply:
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.check_art:
        from PIL import Image
        world = next(m for m in document["maps"] if m["id"] == "world_72")
        overview = Image.open(args.root / "assets" / world["backgroundAssetPath"]).convert("RGB")
        columns, rows = world["backgroundTileColumns"], world["backgroundTileRows"]
        tiles = [Image.open(args.root / "assets" / p).convert("RGB") for p in world["backgroundTileAssetPaths"]]
        water = []
        for node in world["nodes"]:
            x, top = node["x"], 1 - node["y"]
            column, row = int(x * columns), int(top * rows)
            tile = tiles[row * columns + column]
            samples = [overview.getpixel((int(x * overview.width), int(top * overview.height))),
                       tile.getpixel((int((x * columns - column) * tile.width),
                                      int((top * rows - row) * tile.height)))]
            if any(blue > red and green > red for red, green, blue in samples):
                water.append((node["cityId"], samples))
        assert not water, ("Blue-water anchors", water)
        print("World overview + detail tile blue-water check: PASS; 72 anchors")
    if args.plots:
        # Scientific diagnostic plot only; source artwork is never modified.
        from PIL import Image
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        args.plots.mkdir(parents=True, exist_ok=True)
        for world in document["maps"]:
            fig, ax = plt.subplots(figsize=(24, 16), dpi=120)
            ax.imshow(Image.open(ROOT / "assets" / world["backgroundAssetPath"]), extent=(0, 1, 0, 1), aspect="auto")
            for node in world["nodes"]:
                ax.plot(node["x"], node["y"], "o", ms=3, color="red", mec="white")
                ax.annotate(node["cityId"], (node["x"], node["y"]), xytext=(4, 4), textcoords="offset points", fontsize=7, color="black", bbox=dict(facecolor="white", alpha=.8, edgecolor="none", pad=.5))
            ax.set_title(world["id"] + " — geographic anchors (not label layout)")
            fig.savefig(args.plots / (world["id"] + ".png"), bbox_inches="tight")
            if world["id"] == "world_72":
                for name, bounds in {
                    "east-asia": (.69, .89, .50, .74),
                    "pacific": (.88, .99, .25, .46),
                    "west-asia": (.52, .70, .54, .74),
                    "europe": (.42, .58, .57, .79),
                    "americas": (.14, .32, .30, .72),
                    "africa": (.40, .60, .25, .62)
                }.items():
                    ax.set_xlim(*bounds[:2])
                    ax.set_ylim(*bounds[2:])
                    fig.savefig(args.plots / (name + ".png"), bbox_inches="tight")
            plt.close(fig)
    print(f"Geography calibration: PASS; {count} nodes; road endpoints valid")


if __name__ == "__main__":
    main()
