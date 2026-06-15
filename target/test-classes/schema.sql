CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY,
    user_id INT,
    total DECIMAL(10, 2)
);
