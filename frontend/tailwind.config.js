/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}", // WICHTIG: Damit Tailwind weiß, wo deine Klassen sind
  ],
  theme: {
    extend: {},
  },
  plugins: [
    // Das brauchen wir für PrimeNG Integration
    require('tailwindcss-primeui')
  ],
}
