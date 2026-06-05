package com.reportagent.service;

import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.chart.ChartColor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChartService {

    private static final int MAX_CATEGORIES = 50;
    private static final int BAR_CHART_WIDTH = 800;
    private static final int BAR_CHART_HEIGHT = 450;
    private static final int PIE_CHART_WIDTH = 800;
    private static final int PIE_CHART_HEIGHT = 500;

    // Color palette for pie chart slices (matching Element Plus palette)
    private static final Paint[] PIE_COLORS = {
            new Color(0x40, 0x9E, 0xFF), // blue
            new Color(0x67, 0xC2, 0x3A), // green
            new Color(0xE6, 0xA2, 0x3C), // orange
            new Color(0xF5, 0x6C, 0x6C), // red
            new Color(0x90, 0x9C, 0x99), // grey
            new Color(0x9B, 0x59, 0xB6), // purple
            new Color(0xF5, 0xDD, 0x4B), // yellow
            new Color(0x00, 0xD1, 0xC1), // teal
    };

    // Font for CJK text rendering
    private static Font getChartFont(int style, int size) {
        // Try common CJK fonts, fallback to SansSerif
        String[] candidates = {"Microsoft YaHei", "SimHei", "WenQuanYi Zen Hei", "Noto Sans CJK SC", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (f.canDisplayUpTo("中文测试数据报表") < 0) {
                return f;
            }
        }
        return new Font("SansSerif", style, size);
    }

    // ─── public API ──────────────────────────────────────────

    /**
     * Generate a bar chart PNG from query result rows.
     * Returns null if data is insufficient (null, empty, single row, or no numeric column).
     */
    public byte[] createBarChart(List<Map<String, Object>> rows) throws IOException {
        if (rows == null || rows.size() < 2) {
            log.debug("柱状图: 数据不足 ({} 行), 跳过", rows == null ? 0 : rows.size());
            return null;
        }

        ChartColumns cols = detectColumns(rows);
        if (cols == null) {
            log.warn("柱状图: 未找到数值列, 跳过");
            return null;
        }

        int originalCount = rows.size();
        List<Map<String, Object>> limited = limitCategories(rows, cols.valueCol);
        if (limited == null) return null;

        // Build dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map<String, Object> row : limited) {
            String label = String.valueOf(row.get(cols.labelCol));
            double value = toDouble(row.get(cols.valueCol));
            dataset.addValue(value, cols.valueCol, label);
        }

        // Create chart
        JFreeChart chart = ChartFactory.createBarChart(
                cols.valueCol,           // title
                cols.labelCol,           // x-axis label
                cols.valueCol,           // y-axis label
                dataset,
                PlotOrientation.VERTICAL,
                true,   // legend
                true,   // tooltip
                false   // urls
        );

        styleChart(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(0xE0, 0xE0, 0xE0));
        plot.setRangeGridlinePaint(new Color(0xE0, 0xE0, 0xE0));
        plot.setRangeAxisLocation(org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);

        // Blue gradient bar color matching frontend #409EFF → #79bbff
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        GradientPaint gp = new GradientPaint(
                0, 0, new Color(0x40, 0x9E, 0xFF),
                0, 0, new Color(0x79, 0xBB, 0xFF)
        );
        renderer.setSeriesPaint(0, gp);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);

        // Rotate labels if many categories
        CategoryAxis domainAxis = plot.getDomainAxis();
        applyFont(domainAxis);
        if (limited.size() > 6) {
            domainAxis.setCategoryLabelPositions(
                    CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 6.0));
        }

        applyFont(plot.getRangeAxis());

        // Subtitle for truncated data
        if (originalCount > MAX_CATEGORIES) {
            TextTitle subtitle = new TextTitle("展示前 " + MAX_CATEGORIES + " / 共 " + originalCount + " 个分类",
                    getChartFont(Font.PLAIN, 11));
            subtitle.setPaint(Color.GRAY);
            subtitle.setPosition(RectangleEdge.BOTTOM);
            chart.addSubtitle(subtitle);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(bos, chart, BAR_CHART_WIDTH, BAR_CHART_HEIGHT);
        return bos.toByteArray();
    }

    /**
     * Generate a donut/ring pie chart PNG from query result rows.
     * Returns null if data is insufficient.
     */
    public byte[] createPieChart(List<Map<String, Object>> rows) throws IOException {
        if (rows == null || rows.size() < 2) {
            log.debug("饼图: 数据不足 ({} 行), 跳过", rows == null ? 0 : rows.size());
            return null;
        }

        ChartColumns cols = detectColumns(rows);
        if (cols == null) {
            log.warn("饼图: 未找到数值列, 跳过");
            return null;
        }

        int originalCount = rows.size();
        List<Map<String, Object>> limited = limitCategories(rows, cols.valueCol);
        if (limited == null) return null;

        // Build dataset
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        for (Map<String, Object> row : limited) {
            String label = String.valueOf(row.get(cols.labelCol));
            double value = toDouble(row.get(cols.valueCol));
            dataset.setValue(label, value);
        }

        JFreeChart chart = ChartFactory.createRingChart(
                cols.valueCol,   // title
                dataset,
                true,   // legend
                true,   // tooltip
                false   // urls
        );

        styleChart(chart);

        // Style as donut (matching frontend's radius: ['30%', '70%'])
        RingPlot plot = (RingPlot) chart.getPlot();
        plot.setSectionDepth(0.40);  // donut thickness
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);

        // Label generator: show name + value + percentage
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
                "{0}: {1} ({2})", NumberFormat.getIntegerInstance(), NumberFormat.getPercentInstance()
        ));
        plot.setLabelFont(getChartFont(Font.PLAIN, 10));
        plot.setLabelBackgroundPaint(new Color(255, 255, 255, 200));
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);

        // Color palette
        for (int i = 0; i < limited.size(); i++) {
            plot.setSectionPaint(i, PIE_COLORS[i % PIE_COLORS.length]);
        }

        // Legend font
        chart.getLegend().setItemFont(getChartFont(Font.PLAIN, 11));

        // Subtitle for truncated data
        if (originalCount > MAX_CATEGORIES) {
            TextTitle subtitle = new TextTitle("展示前 " + MAX_CATEGORIES + " / 共 " + originalCount + " 个分类",
                    getChartFont(Font.PLAIN, 11));
            subtitle.setPaint(Color.GRAY);
            subtitle.setPosition(RectangleEdge.BOTTOM);
            chart.addSubtitle(subtitle);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(bos, chart, PIE_CHART_WIDTH, PIE_CHART_HEIGHT);
        return bos.toByteArray();
    }

    // ─── column detection ────────────────────────────────────

    private static class ChartColumns {
        String labelCol;
        String valueCol;
        ChartColumns(String l, String v) { labelCol = l; valueCol = v; }
    }

    /**
     * Detect label and value columns from the first data row.
     * Matches the frontend ReportChat.vue detectColumns logic:
     * - First column = label
     * - First numeric column after the first = value
     * - Fallback: scan all columns (except label) for numeric
     */
    private ChartColumns detectColumns(List<Map<String, Object>> rows) {
        Map<String, Object> firstRow = rows.get(0);
        // Use keySet from first row; LinkedHashMap preserves SQL column order
        List<String> keys = new ArrayList<>(firstRow.keySet());
        if (keys.isEmpty()) return null;

        String labelCol = null;
        String valueCol = null;

        // First key = label
        for (String key : keys) {
            if (labelCol == null) {
                labelCol = key;
                continue;
            }
            if (valueCol == null && isNumeric(firstRow.get(key))) {
                valueCol = key;
                break;
            }
        }

        // Fallback: scan all columns except label
        if (valueCol == null) {
            for (String key : keys) {
                if (key.equals(labelCol)) continue;
                if (isNumeric(firstRow.get(key))) {
                    valueCol = key;
                    break;
                }
            }
        }

        if (valueCol == null) return null;
        return new ChartColumns(labelCol, valueCol);
    }

    private boolean isNumeric(Object value) {
        if (value == null) return false;
        if (value instanceof Number) return true;
        if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ─── helpers ─────────────────────────────────────────────

    /**
     * Limit to top MAX_CATEGORIES by value, sorted descending.
     */
    private List<Map<String, Object>> limitCategories(List<Map<String, Object>> rows, String valueCol) {
        if (rows.size() <= MAX_CATEGORIES) return rows;

        return rows.stream()
                .sorted((a, b) -> Double.compare(toDouble(b.get(valueCol)), toDouble(a.get(valueCol))))
                .limit(MAX_CATEGORIES)
                .collect(Collectors.toList());
    }

    /**
     * Apply common chart styling: white background, CJK fonts, no border.
     */
    private void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(getChartFont(Font.BOLD, 16));
        chart.getLegend().setItemFont(getChartFont(Font.PLAIN, 12));
        chart.getLegend().setFrame(BlockBorder.NONE);

        // Remove chart border
        chart.setBorderVisible(false);
    }

    /**
     * Apply CJK font to a chart axis.
     */
    private void applyFont(org.jfree.chart.axis.Axis axis) {
        axis.setLabelFont(getChartFont(Font.PLAIN, 12));
        axis.setTickLabelFont(getChartFont(Font.PLAIN, 11));
    }
}
