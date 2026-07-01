import re

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "r") as f:
    content = f.read()

# Fix handlePaymentEvents
content = content.replace("""                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process payment event", e);
                    }
                });""", """                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process payment event", e);
                    }
                });""")

# Fix handleOrderEvents
content = content.replace("""                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process order event inner", e);
                    }
                });""", """                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process order event inner", e);
                    }
                });""")

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "w") as f:
    f.write(content)
