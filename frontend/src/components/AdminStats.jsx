import React, { useEffect, useState } from 'react';
import { getAdminStats } from '../services/adminApi';

const AdminStats = () => {
    const [stats, setStats] = useState({
        totalUsers: 0,
        totalProducts: 0,
        totalOrders: 0
    });

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const data = await getAdminStats();
                setStats(data);
            } catch (error) {
                console.error("Lỗi lấy thống kê:", error);
            }
        };
        fetchStats();
    }, []);

    const cardStyle = {
        flex: 1,
        padding: '20px',
        borderRadius: '12px',
        backgroundColor: '#fff',
        boxShadow: '0 4px 15px rgba(0,0,0,0.05)',
        border: '1px solid #f0f0f0',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: '200px'
    };

    const iconStyle = {
        fontSize: '2rem',
        marginBottom: '10px'
    };

    const countStyle = {
        fontSize: '1.8rem',
        fontWeight: 'bold',
        color: '#2c3e50'
    };

    const labelStyle = {
        color: '#7f8c8d',
        fontSize: '0.9rem',
        textTransform: 'uppercase',
        letterSpacing: '1px'
    };

    return (
        <div style={{ display: 'flex', gap: '20px', marginBottom: '30px', flexWrap: 'wrap' }}>
            {/* Thẻ Users */}
            <div style={cardStyle}>
                <div style={iconStyle}>👥</div>
                <div style={countStyle}>{stats.totalUsers}</div>
                <div style={labelStyle}>Người dùng</div>
            </div>

            {/* Thẻ Products */}
            <div style={cardStyle}>
                <div style={iconStyle}>📱</div>
                <div style={countStyle}>{stats.totalProducts}</div>
                <div style={labelStyle}>Sản phẩm</div>
            </div>

            {/* Thẻ Orders */}
            <div style={cardStyle}>
                <div style={iconStyle}>📜</div>
                <div style={countStyle}>{stats.totalOrders}</div>
                <div style={labelStyle}>Đơn hàng</div>
            </div>
        </div>
    );
};

export default AdminStats;
