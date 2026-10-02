import React, { useEffect, useState } from 'react';
import { getRevenueOverTime, getOrdersByStatus, getTopProducts, getReportSummary } from '../../services/reportApi';
import ErrorMessage from '../../components/ErrorMessage';
import './SalesDashboard.css';

const RANGES = [
  { value: '7d', label: '7 ngày' },
  { value: '30d', label: '30 ngày' },
  { value: '12m', label: '12 tháng' },
];

// Same 4 colors as the .status-badge text colors in index.css - kept in sync
// on purpose (see MASTER_PROMPT_V6 2.3.d): PENDING/CONFIRMED/PAID/CANCELLED.
const STATUS_COLORS = {
  PENDING: '#9A6B00',
  CONFIRMED: '#1D5FA8',
  PAID: '#2E7D32',
  CANCELLED: '#C0392B',
};

const formatVnd = (value) => `${Number(value || 0).toLocaleString('vi-VN')}₫`;

/**
 * NOTE ON CHARTS: MASTER_PROMPT_V6 2.1 asks for Recharts, but `npm install`
 * could not reach the npm registry in this environment (offline sandbox -
 * verified with `npm view recharts version`, got a 403 from the egress
 * proxy). Per the prompt's own fallback instruction ("nếu không cài được
 * package mới... tạm thời vẽ biểu đồ thanh/đường bằng SVG thuần thay thế"),
 * the line/area and donut charts below are hand-rolled inline SVG with no
 * external dependency. Swap these for Recharts once `recharts` can actually
 * be installed - the data shape from reportApi already matches what
 * Recharts' <LineChart>/<PieChart> would expect.
 */
const RevenueChart = ({ data }) => {
  const width = 720;
  const height = 260;
  const padding = { top: 16, right: 16, bottom: 32, left: 64 };
  const innerW = width - padding.left - padding.right;
  const innerH = height - padding.top - padding.bottom;

  const values = data.map((d) => Number(d.revenue) || 0);
  const maxValue = Math.max(1, ...values);
  const stepX = data.length > 1 ? innerW / (data.length - 1) : 0;

  const points = data.map((d, i) => {
    const x = padding.left + stepX * i;
    const y = padding.top + innerH - (Number(d.revenue) / maxValue) * innerH;
    return { x, y, d };
  });

  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
  const areaPath =
    points.length > 0
      ? `${linePath} L ${points[points.length - 1].x.toFixed(1)} ${(padding.top + innerH).toFixed(1)} ` +
      `L ${points[0].x.toFixed(1)} ${(padding.top + innerH).toFixed(1)} Z`
      : '';

  // Show at most ~8 x-axis labels so dense ranges (30 days, 12 months) stay readable.
  const labelEvery = Math.max(1, Math.ceil(data.length / 8));

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="sd-chart-svg" role="img" aria-label="Doanh thu theo thời gian">
      {[0, 0.25, 0.5, 0.75, 1].map((t) => {
        const y = padding.top + innerH * (1 - t);
        return (
          <g key={t}>
            <line x1={padding.left} y1={y} x2={width - padding.right} y2={y} className="sd-grid-line" />
            <text x={padding.left - 8} y={y + 4} textAnchor="end" className="sd-axis-label">
              {Math.round((maxValue * t) / 1000)}k
            </text>
          </g>
        );
      })}

      {areaPath && <path d={areaPath} className="sd-area-fill" />}
      {linePath && <path d={linePath} className="sd-line-stroke" />}

      {points.map((p, i) => (
        <g key={p.d.period}>
          <circle cx={p.x} cy={p.y} r={3} className="sd-point">
            <title>{`${p.d.period}: ${formatVnd(p.d.revenue)} (${p.d.orderCount} đơn)`}</title>
          </circle>
          {i % labelEvery === 0 && (
            <text x={p.x} y={height - 8} textAnchor="middle" className="sd-axis-label">
              {p.d.period.length > 7 ? p.d.period.slice(5) : p.d.period}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
};

const StatusDonut = ({ counts }) => {
  const entries = Object.entries(counts).filter(([, v]) => v > 0);
  const total = entries.reduce((sum, [, v]) => sum + v, 0);
  const size = 200;
  const r = 80;
  const cx = size / 2;
  const cy = size / 2;
  const strokeWidth = 32;
  const circumference = 2 * Math.PI * r;

  let offsetSoFar = 0;

  return (
    <div className="sd-donut-wrap">
      <svg viewBox={`0 0 ${size} ${size}`} className="sd-donut-svg" role="img" aria-label="Tỉ lệ đơn hàng theo trạng thái">
        {total === 0 ? (
          <circle cx={cx} cy={cy} r={r} fill="none" stroke="#E1E9DE" strokeWidth={strokeWidth} />
        ) : (
          entries.map(([status, value]) => {
            const fraction = value / total;
            const dash = fraction * circumference;
            const gap = circumference - dash;
            const rotation = (offsetSoFar / total) * 360 - 90;
            offsetSoFar += value;
            return (
              <circle
                key={status}
                cx={cx}
                cy={cy}
                r={r}
                fill="none"
                stroke={STATUS_COLORS[status] || '#999'}
                strokeWidth={strokeWidth}
                strokeDasharray={`${dash} ${gap}`}
                transform={`rotate(${rotation} ${cx} ${cy})`}
              >
                <title>{`${status}: ${value}`}</title>
              </circle>
            );
          })
        )}
        <text x={cx} y={cy + 5} textAnchor="middle" className="sd-donut-total">
          {total}
        </text>
      </svg>
      <ul className="sd-donut-legend">
        {Object.entries(counts).map(([status, value]) => (
          <li key={status}>
            <span className="sd-legend-dot" style={{ backgroundColor: STATUS_COLORS[status] || '#999' }} />
            {status} <strong>{value}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
};

/**
 * Sales Dashboard - manager-facing revenue report, ADMIN only. This component
 * owns its own loading/error/data state entirely separate from the parent
 * AdminDashboard (which has its own loading/errorMsg for products/categories/
 * orders/users) so the two never fight over the same state.
 */
const SalesDashboard = () => {
  const [range, setRange] = useState('30d');
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [summary, setSummary] = useState(null);
  const [revenueSeries, setRevenueSeries] = useState([]);
  const [statusCounts, setStatusCounts] = useState({ PENDING: 0, CONFIRMED: 0, PAID: 0, CANCELLED: 0 });
  const [topProducts, setTopProducts] = useState([]);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setErrorMsg('');
      try {
        const [summaryData, revenueData, statusData, topProductsData] = await Promise.all([
          getReportSummary(range),
          getRevenueOverTime(range),
          getOrdersByStatus(),
          getTopProducts(5, range),
        ]);
        if (cancelled) return;
        setSummary(summaryData);
        setRevenueSeries(revenueData);
        setStatusCounts(statusData);
        setTopProducts(topProductsData);
      } catch (error) {
        if (!cancelled) setErrorMsg(error.friendlyMessage || 'Could not load the sales report.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => { cancelled = true; };
  }, [range]);

  return (
    <div className="sd-root">
      <div className="sd-range-tabs">
        {RANGES.map((r) => (
          <button
            key={r.value}
            className={range === r.value ? 'btn-primary' : 'btn-outline'}
            onClick={() => setRange(r.value)}
          >
            {r.label}
          </button>
        ))}
      </div>

      {errorMsg && <ErrorMessage message={errorMsg} onRetry={() => setRange((r) => r)} />}

      {loading ? (
        <div className="sd-loading"><div className="spinner"></div></div>
      ) : (
        <>
          <div className="sd-summary-grid">
            <div className="sd-summary-card">
              <div className="sd-summary-icon">💰</div>
              <div className="sd-summary-value">{formatVnd(summary?.totalRevenue)}</div>
              <div className="sd-summary-label">Tổng doanh thu</div>
            </div>
            <div className="sd-summary-card">
              <div className="sd-summary-icon">🧾</div>
              <div className="sd-summary-value">{summary?.totalOrders ?? 0}</div>
              <div className="sd-summary-label">Tổng đơn hàng</div>
            </div>
            <div className="sd-summary-card">
              <div className="sd-summary-icon">📊</div>
              <div className="sd-summary-value">{formatVnd(summary?.averageOrderValue)}</div>
              <div className="sd-summary-label">Giá trị đơn trung bình</div>
            </div>
            {summary && 'newCustomers' in summary && (
              <div className="sd-summary-card">
                <div className="sd-summary-icon">🌱</div>
                <div className="sd-summary-value">{summary.newCustomers}</div>
                <div className="sd-summary-label">Khách hàng mới</div>
              </div>
            )}
          </div>

          <div className="sd-charts-grid">
            <div className="sd-chart-card">
              <h4>Doanh thu theo thời gian</h4>
              {revenueSeries.length > 0
                ? <RevenueChart data={revenueSeries} />
                : <p className="sd-empty">Chưa có dữ liệu doanh thu trong khoảng này.</p>}
            </div>
            <div className="sd-chart-card">
              <h4>Đơn hàng theo trạng thái</h4>
              <StatusDonut counts={statusCounts} />
            </div>
          </div>

          <div className="sd-chart-card sd-top-products">
            <h4>Top sản phẩm bán chạy</h4>
            {topProducts.length === 0 ? (
              <p className="sd-empty">Chưa có sản phẩm nào bán được trong khoảng này.</p>
            ) : (
              <div className="admin-table-wrap scroll-touch">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Sản phẩm</th>
                      <th>Số lượng đã bán</th>
                      <th>Doanh thu</th>
                    </tr>
                  </thead>
                  <tbody>
                    {topProducts.map((p) => (
                      <tr key={p.productId}>
                        <td>{p.productName}</td>
                        <td>{p.quantitySold}</td>
                        <td className="sd-revenue-cell">{formatVnd(p.revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default SalesDashboard;
