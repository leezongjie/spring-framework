/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction;


import jakarta.resource.cci.ResultSet;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.cglib.proxy.Proxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Rob Harrop
 * @author Adrian Colyer
 */
class RollbackAfterCommitFailedTests {

	public static void main(String[] args) throws SQLException {

		String url = "jdbc:mysql://127.1:3306/xts_first";
		String user = "root";
		String pwd = "123456";

		BasicDataSource dataSource = new BasicDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				Connection connection = super.getConnection();
				ConnectionProxy dynamicProxy = new ConnectionProxy(connection);
				Class<?> serviceClass = Connection.class;

				connection = (Connection) Proxy.newProxyInstance(serviceClass.getClassLoader(), new Class[]{serviceClass}, dynamicProxy);
				return connection;
			}
		};
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUrl(url);
		dataSource.setUsername(user);
		dataSource.setPassword(pwd);
		dataSource.setMaxActive(10);
		dataSource.setMinIdle(1);
		dataSource.setConnectionProperties("useUnicode=yes;characterEncoding=utf8;socketTimeout=5000;connectTimeout=500");

		DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
		dataSourceTransactionManager.setDataSource(dataSource);

		TransactionTemplate transactionTemplate = new TransactionTemplate();
		transactionTemplate.setTransactionManager(dataSourceTransactionManager);

		long id = System.currentTimeMillis();

		try {
			transactionTemplate.execute(new TransactionCallback() {
				@SneakyThrows
				@Override
				public Object doInTransaction(TransactionStatus status) {
					Connection conn = DataSourceUtils.getConnection(dataSource);
					Statement stat;
					String sql = "insert into account values ('" + id + "',1,1);";
					stat = conn.createStatement();

					stat.execute(sql);

					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
						@Override
						public void afterCompletion(int status) {
							System.out.println("afterCompletion提交状态为(STATUS_COMMITTED=0;STATUS_ROLLED_BACK=1;STATUS_UNKNOWN=2)：" + status);
						}
					});

					return null;
				}
			});
		} finally {
			Statement stat = dataSource.getConnection().createStatement();
			ResultSet rs = stat.executeQuery("select * from account where account_no='" + id + "'");
			String rsId = null;
			while (rs.next()) {
				rsId = rs.getString("account_no");
				break;
			}
			System.out.println("db查询结果：" + rsId + "，db事务 " + (rsId == null ? "未提交" : "已提交"));
		}
	}


	static class ConnectionProxy implements InvocationHandler {
		private Object targetObject;

		public ConnectionProxy(Object targetObject) {
			this.targetObject = targetObject;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object invoke = method.invoke(targetObject, args);
			if (method.getName().equalsIgnoreCase("commit")) {
				System.out.println("模拟commit后oom");
				throw new OutOfMemoryError("mock");
			}
			return invoke;
		}
	}

}
