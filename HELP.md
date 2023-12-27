Ways to improve:

- Add some kind of relational database instead of storing all the user data in memory.
- Use the relational database to perform transfers using sql transactions and locking - this will improve the
  performance of concurrent transactions
- Save Transfer objects to database to preserve transaction history
- Add authentication using Spring Security to prevent unauthorized transactions
- Add OpenApi specification for service endpoints to make it easier to implement frontend/client services