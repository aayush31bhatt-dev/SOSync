# 🗺️ Smart Community SOS - Heatmap Feature Implementation

## Overview
The heatmap feature provides an interactive, data-driven visualization of crime hotspots across India using advanced geospatial analysis and real-time heatmap rendering.

## Features Implemented

### 1. **Interactive Controls Panel** 
Located at the top of the Map Screen, users can adjust heatmap visualization in real-time:

- **Radius Control** (10-50px)
  - Controls the radius of each heat point
  - Smaller values = more precise, granular visualization
  - Larger values = smoother, broader patterns
  - Default: 25px

- **Blur Radius Control** (5-40px)
  - Controls the smoothness of transitions between heat points
  - Lower values = sharper boundaries between hot/cold zones
  - Higher values = gradient smooth transitions
  - Default: 20px

- **Minimum Opacity Control** (0.0-0.5)
  - Controls transparency of low-intensity areas
  - Lower = more transparent low-crime areas
  - Higher = more visible low-crime areas
  - Default: 0.16

### 2. **Enhanced Leaflet.js Map**

The map now includes:

- **Color Legend** (Bottom-Right Corner)
  - Shows 4-level crime intensity scale
  - 🔵 Blue = Low-risk areas
  - 🟢 Green = Medium-risk areas
  - 🟡 Yellow = High-risk areas
  - 🔴 Red = Critical-risk areas

- **Zoom Level Indicator** (Top-Right Corner)
  - Displays current map zoom level
  - Updates in real-time as user zooms

- **Status Badge** (Top-Left Corner)
  - Shows incident count and cell aggregation
  - Displays current heatmap settings (radius, blur)
  - Updates when controls change

- **Live OpenStreetMap Basemap**
  - Street, building, and landmark details remain visible
  - Allows users to identify specific locations within heatmaps
  - Graceful fallback message if tiles fail to load

### 3. **Data Summary Section**
Below the map:

- **Dataset Summary Card**
  - Total incident count
  - Average risk score
  - Rendered heat cells
  - Dataset filename

- **Top Cities Card**
  - Lists cities with highest incident counts
  - Sorted by frequency

- **Top Crime Types Card**
  - Shows most common crime categories
  - Examples: theft, assault, harassment, vehicular theft, pickpocketing

## Architecture

### Frontend (Android/Kotlin)
```
MapScreen.kt
├── mapState management (reload token, controls state)
├── HeatmapControlsCard (radius, blur, opacity sliders)
├── HeatmapCard (render + status)
├── LeafletHeatmapView (WebView wrapper)
└── buildLeafletHeatmapHtml (dynamic HTML/JS generation)
```

### Backend Integration
- **API Base**: `http://10.0.2.2:8000` (emulator localhost)
- **Endpoints Called**:
  - `GET /api/heatmap/summary` → Stats & metadata
  - `GET /api/heatmap/grid?rows=120&cols=120` → Aggregated heatmap points

### Rendering Stack
1. **Leaflet.js 1.9.4** - Base map library
2. **leaflet.heat** - Heat layer plugin
3. **OpenStreetMap** - Tile provider
4. **Canvas Rendering** - Optimized for performance

## Usage Guide

### Starting the App
1. Ensure backend is running on port 8000
2. Open the app and navigate to Map Screen
3. Wait for "Connecting to backend..." to complete

### Adjusting Visualization
1. Use sliders in "Heatmap Intensity Controls" section
2. **Radius**: Make patterns sharper (low) or broader (high)
3. **Blur**: Adjust gradient smoothness
4. **Opacity**: Control visibility of low-risk areas
5. Map updates instantly as you adjust controls

### Interpreting the Heatmap
- **Dark Red/Orange zones** = High-crime clusters (critical risk)
- **Yellow zones** = Moderate-crime areas
- **Green zones** = Low-crime neighborhoods
- **Blue zones** = Safest areas
- **Click anywhere** to see exact coordinates (standard Leaflet behavior)
- **Zoom in/out** to see fine-grained or broad patterns

## Data Flow

```
User Opens MapScreen
    ↓
loadMapData() fetches:
  ├─ /api/heatmap/summary (async)
  └─ /api/heatmap/grid (async)
    ↓
Backend aggregates:
  ├─ Raw incidents from CSV
  ├─ Groups into 120x120 grid cells
  ├─ Calculates weights per cell
    ↓
Frontend receives JSON:
  ├─ Grid points [lat, lon, weight]
  ├─ Bounds [min/max lat/lon]
  ├─ Statistics (counts, avg risk)
    ↓
buildLeafletHeatmapHtml() generates dynamic HTML with:
  ├─ Leaflet initialization
  ├─ Heat layer with user settings
  ├─ Legend, status, zoom indicator
    ↓
WebView renders interactive map
    ↓
User adjusts controls → settings applied → WebView reloads
```

## Performance Considerations

- **Grid Resolution**: 120x120 cells balances detail vs performance
- **Radius/Blur**: Defaults optimized for India region at zoom level 5
- **Canvas Rendering**: Faster than DOM for large point sets
- **Tile Caching**: OpenStreetMap tiles cached automatically
- **WebView Optimization**: 
  - DOM storage enabled
  - Image loading disabled for rendering speed
  - Built-in zoom controls used (native performance)

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Connecting to backend..." hangs | Ensure `python backend/main.py` is running on port 8000 |
| Map tiles don't show | Emulator may be blocking remote access; heatmap data still renders |
| Controls don't update map | WebView may need refresh; tap the card to reload |
| Low-risk areas not visible | Increase "Min Opacity" slider |
| Heatmap too blurry | Decrease "Blur Radius" slider |
| Patterns too granular | Increase "Radius" slider |

## Future Enhancements

- [ ] City-level filtering (dropdown to filter by city)
- [ ] Toggle between grid view and raw points view
- [ ] Time-series slider (incidents by date range)
- [ ] Crime type filtering
- [ ] Custom gradient/colormap selection
- [ ] Export heatmap as image
- [ ] Share safety report with contacts
- [ ] Real-time incident streaming overlay

## Files Modified

- `app/src/main/java/com/smartcommunity/sos/ui/map/MapScreen.kt` - Main implementation
  - Added `HeatmapSettings` data class
  - Added `HeatmapControlsCard` composable
  - Updated `buildLeafletHeatmapHtml()` with legend and dynamic settings
  - Enhanced Leaflet.js configuration

## Testing Checklist

- [x] Backend API endpoints responding
- [x] Heatmap grid rendering without errors
- [x] Controls update map in real-time
- [x] Legend displays correctly
- [x] Zoom indicator working
- [x] Status badge shows correct data
- [x] Summary stats loading correctly
- [x] Responsive on different screen sizes
- [x] Data aggregation working (120x120 grid)

---

**Implementation Date**: April 2026  
**Backend**: FastAPI with LRU caching  
**Frontend**: Jetpack Compose + Leaflet.js  
**Status**: ✅ Production Ready
