import React from 'react';
import './CategoryFilter.css';

/**
 * Category chips driven entirely by the database (GET /api/categories) -
 * no hard-coded EcoGreen category list here.
 */
const CategoryFilter = ({ categories, selectedCategoryId, onSelectCategory }) => {
  const options = [{ id: null, name: 'All' }, ...categories];

  return (
    <div className="category-filter-container">
      <div className="category-filter-list">
        {options.map((cat) => (
          <button
            key={cat.id ?? 'all'}
            type="button"
            className={`category-item ${selectedCategoryId === cat.id ? 'active' : ''}`}
            onClick={() => onSelectCategory(cat.id)}
          >
            {cat.name}
          </button>
        ))}
      </div>
    </div>
  );
};

export default CategoryFilter;
