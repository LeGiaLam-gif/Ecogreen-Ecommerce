// Generic product-image resolver.
//
// products.image (from the database) may contain either:
//   - a bare filename ("reusable-bottle.jpg") that is expected to live under
//     src/assets/products/, or
//   - a full http(s) URL, which is used as-is, or
//   - an uploaded image served by the API ("/api/v1/files/<key>"), which is used as-is.
// If nothing resolves, a local SVG placeholder is shown - never a random
// remote placeholder URL.
import placeholder from '../assets/products/placeholder.svg';

export const imageModules = import.meta.glob('../assets/products/*.{png,jpg,jpeg,webp,svg}', { eager: true });

export const resolveProductImage = (imagePath) => {
  if (!imagePath) return placeholder;

  if (imagePath.startsWith('http://') || imagePath.startsWith('https://')) {
    return imagePath;
  }

  if (imagePath.startsWith('/api/')) {
    return imagePath;
  }

  const filename = imagePath.split('/').pop();
  const targetKey = Object.keys(imageModules).find((key) => key.endsWith(filename));

  if (targetKey && imageModules[targetKey]) {
    return imageModules[targetKey].default || imageModules[targetKey];
  }

  return placeholder;
};

export default resolveProductImage;
